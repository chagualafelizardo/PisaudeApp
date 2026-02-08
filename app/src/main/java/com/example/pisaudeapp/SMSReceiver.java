package com.example.pisaudeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = "SMSReceiver";
    private static final String SERVER_PHONE_NUMBER = "+258851655626"; // Número do servidor

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) {
            return;
        }

        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Log.d(TAG, "📱 SMS recebido!");

            try {
                // Extrair mensagens SMS
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    if (pdus != null) {
                        for (Object pdu : pdus) {
                            SmsMessage smsMessage;

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                String format = bundle.getString("format");
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                            }

                            String senderPhone = smsMessage.getOriginatingAddress();
                            String messageBody = smsMessage.getMessageBody();

                            Log.d(TAG, "📨 De: " + senderPhone);
                            Log.d(TAG, "📝 Mensagem: " + messageBody);

                            // Processar se for do número do servidor
                            if (senderPhone != null && senderPhone.contains(SERVER_PHONE_NUMBER)) {
                                processServerSMS(context, messageBody, senderPhone);
                            }

                            // Processar agendamento se for de um paciente
                            processAppointmentSMS(context, messageBody, senderPhone);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao processar SMS", e);
            }
        }
    }

    private void processServerSMS(Context context, String message, String senderPhone) {
        Log.d(TAG, "🔔 SMS do servidor recebido: " + message);

        // Exibir notificação
        Toast.makeText(context, "📨 SMS do servidor: " + message, Toast.LENGTH_LONG).show();
    }

    private void processAppointmentSMS(Context context, String message, String senderPhone) {
        // Normalizar a mensagem
        String normalizedMessage = message.trim();

        Log.d(TAG, "📝 Mensagem recebida: " + normalizedMessage);

        // Verificar se é uma mensagem de agendamento
        String lowerMessage = normalizedMessage.toLowerCase();
        boolean isAppointmentMessage = lowerMessage.startsWith("consulta ") ||
                lowerMessage.startsWith("agendar ") ||
                lowerMessage.startsWith("marcar ");

        if (isAppointmentMessage) {
            Log.d(TAG, "✅ Mensagem de agendamento detectada!");

            // Enviar para a API
            ApiService apiService = new ApiService(context);

            apiService.processAppointmentSMS(senderPhone, normalizedMessage, new ApiService.AppointmentCallback() {
                @Override
                public void onSuccess(ApiService.Appointment appointment) {
                    Log.d(TAG, "✅ Agendamento criado: " + appointment.toString());

                    // Exibir confirmação
                    String confirmation;
                    if (appointment.nid != null) {
                        confirmation = "✅ Agendamento #" + appointment.id +
                                " criado para " + formatDate(appointment.appointmentDate) +
                                " às " + appointment.appointmentTime +
                                "\nNID: " + appointment.nid;
                    } else {
                        confirmation = "✅ Agendamento #" + appointment.id +
                                " criado para " + formatDate(appointment.appointmentDate) +
                                " às " + appointment.appointmentTime;
                    }

                    Toast.makeText(context, confirmation, Toast.LENGTH_LONG).show();

                    // Enviar SMS de confirmação para o paciente
                    sendConfirmationSMS(context, senderPhone, appointment);

                    // Enviar broadcast para atualizar a tela de agendamentos
                    Intent updateIntent = new Intent("UPDATE_APPOINTMENTS_LIST");
                    context.sendBroadcast(updateIntent);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "❌ Erro ao criar agendamento: " + error);

                    // Enviar SMS de erro para o paciente
                    String errorMessage = "❌ Não foi possível agendar:\n" + error +
                            "\n\nFormatos aceites:\n" +
                            "• consulta NID DD/MM/AAAA HH:MM\n" +
                            "• consulta DD/MM/AAAA HH:MM\n\n" +
                            "Exemplos:\n" +
                            "• consulta 0110000001/2015/00396 25/12/2024 14:30\n" +
                            "• consulta 25/12/2024 14:30";

                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();

                    // Opcional: enviar SMS com instruções
                    sendErrorSMS(context, senderPhone, error);
                }
            });
        }
    }

    private void sendConfirmationSMS(Context context, String phone, ApiService.Appointment appointment) {
        try {
            String message;

            if (appointment.nid != null && !appointment.nid.isEmpty()) {
                // Mensagem com NID
                message = String.format(
                        "✅ Agendamento CONFIRMADO!\n\n" +
                                "🆔 NID: %s\n" +
                                "📅 Data: %s\n" +
                                "⏰ Hora: %s\n" +
                                "👨‍⚕️ Médico: %s\n" +
                                "📋 Status: %s\n" +
                                "🔢 ID Agendamento: %d\n\n" +
                                "Por favor, chegue 15 minutos antes.",
                        appointment.nid,
                        formatDate(appointment.appointmentDate),
                        appointment.appointmentTime,
                        appointment.doctorName,
                        appointment.status,
                        appointment.id
                );
            } else {
                // Mensagem sem NID
                message = String.format(
                        "✅ Agendamento CONFIRMADO!\n\n" +
                                "📅 Data: %s\n" +
                                "⏰ Hora: %s\n" +
                                "👨‍⚕️ Médico: %s\n" +
                                "📋 Status: %s\n" +
                                "🔢 ID Agendamento: %d\n\n" +
                                "Por favor, chegue 15 minutos antes.\n" +
                                "⚠️ Guarde este ID para referência.",
                        formatDate(appointment.appointmentDate),
                        appointment.appointmentTime,
                        appointment.doctorName,
                        appointment.status,
                        appointment.id
                );
            }

            // Enviar SMS de confirmação
            ApiService apiService = new ApiService(context);
            ApiService.Patient tempPatient = new ApiService.Patient(
                    appointment.patientId,
                    appointment.patientName,
                    phone,
                    "M",
                    appointment.status,
                    message
            );

            apiService.sendSMS(tempPatient, new ApiService.SmsCallback() {
                @Override
                public void onSmsSent(String phone, boolean success, int patientId, String message) {
                    Log.d(TAG, "📨 SMS de confirmação enviado: " + success);
                }

                @Override
                public void onSmsProgress(String phone, String message, int patientId, int progress, int total) {
                    // Não usado
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao enviar SMS de confirmação", e);
        }
    }

    private void sendErrorSMS(Context context, String phone, String error) {
        try {
            String message = "❌ Não foi possível agendar:\n" + error +
                    "\n\nFormatos aceites:\n" +
                    "• consulta NID DD/MM/AAAA HH:MM\n" +
                    "• consulta DD/MM/AAAA HH:MM\n\n" +
                    "Exemplos:\n" +
                    "• consulta 0110000001/2015/00396 25/12/2024 14:30\n" +
                    "• consulta 25/12/2024 14:30";

            ApiService.Patient tempPatient = new ApiService.Patient(
                    0,
                    "Paciente",
                    phone,
                    "M",
                    "erro",
                    message
            );

            ApiService apiService = new ApiService(context);
            apiService.sendSMS(tempPatient, null);

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao enviar SMS de erro", e);
        }
    }

    private String formatDate(String isoDate) {
        try {
            // Converter de YYYY-MM-DD para DD/MM/YYYY
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

            Date date = inputFormat.parse(isoDate);
            return outputFormat.format(date);
        } catch (ParseException e) {
            // Se não conseguir converter, retorna o original
            return isoDate;
        } catch (Exception e) {
            return isoDate;
        }
    }
}