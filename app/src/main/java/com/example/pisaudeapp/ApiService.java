package com.example.pisaudeapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiService {
    private Context context;
    private RequestQueue requestQueue;
    private SmsManager smsManager;

    // URL base da API
    private static final String BASE_URL = "http://192.168.4.211:5000";

    // Interface para callback 192.168.249.227
    public interface ApiCallback {
        void onSuccess(List<Patient> patients);
        void onError(String error);
    }

    public interface SmsCallback {
        void onSmsSent(String phone, boolean success, int patientId, String message);
        void onSmsProgress(String phone, String message, int patientId, int progress, int total);
    }

    public interface StatusUpdateCallback {
        void onStatusUpdated(boolean success, String message);
    }

    // Classe Patient atualizada com ID
    public static class Patient {
        public int id; // ID do paciente no banco de dados
        public String fullname;
        public String contact;
        public String gender;
        public String textMessageDescription;
        public String stateDescription;

        public Patient(int id, String fullname, String contact, String gender,
                       String stateDescription, String textMessageDescription) {
            this.id = id;
            this.fullname = fullname;
            this.contact = contact;
            this.gender = gender;
            this.textMessageDescription = textMessageDescription;
            this.stateDescription = stateDescription;
        }

        public boolean isValid() {
            return contact != null && !contact.trim().isEmpty() &&
                    textMessageDescription != null && !textMessageDescription.trim().isEmpty();
        }

        public String getFormattedMessage() {
            String greeting = "F".equals(gender) ? "Prezada" : "Prezado";
            return greeting + " " + fullname + ", " + textMessageDescription;
        }

        @Override
        public String toString() {
            return "Patient{id=" + id + ", name='" + fullname + "', phone='" + contact + "'}";
        }
    }

    public ApiService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        this.smsManager = SmsManager.getDefault();
    }

    // Buscar pacientes da API
    public void getPatients(final ApiCallback callback) {
        String url = BASE_URL + "/api/observation";
        Log.d("API_DEBUG", "🔗 Conectando em: " + url);

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            Log.d("API_DEBUG", "✅ Resposta recebida, tamanho: " + response.length());

                            if (response.length() == 0) {
                                Log.w("API_DEBUG", "⚠️ Resposta vazia");
                                callback.onSuccess(new ArrayList<>());
                                return;
                            }

                            List<Patient> patients = parsePatients(response);
                            Log.d("API_DEBUG", "📊 " + patients.size() + " pacientes parseados");
                            callback.onSuccess(patients);

                        } catch (Exception e) {
                            Log.e("API_DEBUG", "💥 Erro inesperado", e);
                            callback.onError("Erro inesperado: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (error.networkResponse != null && error.networkResponse.statusCode == 200) {
                            Log.w("API_DEBUG", "⚠️ API retornou 200 mas Volley reportou erro");
                            try {
                                String responseBody = new String(error.networkResponse.data, "UTF-8");
                                JSONArray jsonArray = new JSONArray(responseBody);
                                List<Patient> patients = parsePatients(jsonArray);
                                callback.onSuccess(patients);
                            } catch (Exception e) {
                                Log.e("API_DEBUG", "❌ Não foi possível parsear resposta", e);
                                callback.onSuccess(new ArrayList<>());
                            }
                        } else {
                            String errorMsg = getVolleyErrorMessage(error);
                            Log.e("API_DEBUG", "💥 " + errorMsg);
                            callback.onError("Falha na conexão: " + errorMsg);
                        }
                    }
                }
        );

        jsonArrayRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        jsonArrayRequest.setShouldCache(false);
        requestQueue.add(jsonArrayRequest);
    }

    // Parse dos dados JSON com ID
    private List<Patient> parsePatients(JSONArray jsonArray) throws JSONException {
        List<Patient> patients = new ArrayList<>();

        if (jsonArray == null) {
            Log.w("API_DEBUG", "JSONArray é nulo");
            return patients;
        }

        Log.d("API_DEBUG", "📋 Iniciando parse de " + jsonArray.length() + " itens");

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject patientJson = jsonArray.getJSONObject(i);

                // Obter ID do paciente (assumindo que o campo é 'id' no JSON)
                int id = patientJson.optInt("id", 0);
                if (id == 0) {
                    // Se não tiver 'id', tenta 'patientId' ou usa o índice como fallback
                    id = patientJson.optInt("patientId", i + 1);
                }

                String fullname = patientJson.optString("fullname", "");
                String contact = patientJson.optString("contact", "");
                String gender = patientJson.optString("gender", "M");
                String textMessage = patientJson.optString("textMessageDescription", "");
                String stateDescription = patientJson.optString("stateDescription", "");

                // Debug do primeiro paciente
                if (i == 0) {
                    Log.d("API_DEBUG", "👤 Paciente exemplo: " +
                            "ID: " + id +
                            ", Nome: " + fullname +
                            ", Contacto: " + contact +
                            ", Género: " + gender);
                }

                Patient patient = new Patient(id, fullname, contact, gender, stateDescription, textMessage);
                if (patient.isValid()) {
                    patients.add(patient);
                } else {
                    Log.w("API_DEBUG", "⚠️ Paciente inválido ID " + id + ": contacto=" + contact);
                }
            } catch (JSONException e) {
                Log.e("API_DEBUG", "❌ Erro ao parsear paciente " + i, e);
            }
        }

        Log.d("API", "📊 Pacientes parseados: " + patients.size() + " válidos de " + jsonArray.length() + " totais");
        return patients;
    }

    // Enviar SMS para um paciente e atualizar status
    public void sendSMS(Patient patient, final SmsCallback callback) {
        try {
            String phoneNumber = formatPhoneNumber(patient.contact);
            String message = patient.getFormattedMessage();

            if (phoneNumber.isEmpty()) {
                Log.w("SMS", "📵 Número inválido: " + patient.contact);
                callback.onSmsSent(patient.contact, false, patient.id, message);

                // Atualizar status como falha
                updateSmsStatus(patient.id, SmsStatusConstants.FAILED, "Número de telefone inválido", new StatusUpdateCallback() {
                    @Override
                    public void onStatusUpdated(boolean success, String updateMessage) {
                        Log.w("SMS", "⚠️ Status atualizado: número inválido");
                    }
                });
                return;
            }

            // Log de início do envio
            String logInfo = "📱 ENVIANDO SMS PARA: " + phoneNumber +
                    " (Paciente: " + patient.fullname + ")";
            String messagePreview = message.length() > 100 ?
                    message.substring(0, 100) + "..." :
                    message;

            Log.d("SMS", "=======================================");
            Log.d("SMS", logInfo);
            Log.d("SMS", "🆔 ID: " + patient.id);
            Log.d("SMS", "📝 Mensagem: " + messagePreview);
            Log.d("SMS", "=======================================");

            // Exibir Toast informativo
            Toast.makeText(context,
                    "Enviando SMS para:\n" + phoneNumber,
                    Toast.LENGTH_SHORT).show();

            // Primeiro atualizar status para "pending"
            updateSmsStatus(patient.id, SmsStatusConstants.PENDING, "Aguardando envio...", new StatusUpdateCallback() {
                @Override
                public void onStatusUpdated(boolean success, String updateMessage) {
                    if (success) {
                        Log.d("SMS", "🔄 Status atualizado para: pending");
                    }
                }
            });

            // Preparar os PendingIntents para receber o resultado
            String sentAction = "SMS_SENT_" + System.currentTimeMillis();
            String deliveredAction = "SMS_DELIVERED_" + System.currentTimeMillis();

            PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    new Intent(sentAction),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    new Intent(deliveredAction),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Registrar BroadcastReceiver para resultado do envio
            BroadcastReceiver sentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String smsStatus = "";
                    String statusMessage = "";
                    boolean success = false;

                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            smsStatus = SmsStatusConstants.SENT;
                            statusMessage = "SMS enviado com sucesso";
                            success = true;
                            Log.d("SMS", "✅ SMS enviado: " + phoneNumber);
                            Toast.makeText(context, "✅ SMS enviado para: " + phoneNumber,
                                    Toast.LENGTH_SHORT).show();
                            break;

                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                            smsStatus = SmsStatusConstants.FAILED;
                            statusMessage = "Falha genérica no envio";
                            success = false;
                            Log.e("SMS", "❌ Falha genérica: " + phoneNumber);
                            break;

                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            smsStatus = SmsStatusConstants.NO_SERVICE;
                            statusMessage = "Sem serviço de rede";
                            success = false;
                            Log.e("SMS", "📶 Sem serviço: " + phoneNumber);
                            break;

                        case SmsManager.RESULT_ERROR_NULL_PDU:
                            smsStatus = SmsStatusConstants.NULL_PDU;
                            statusMessage = "PDU nulo";
                            success = false;
                            Log.e("SMS", "📦 PDU nulo: " + phoneNumber);
                            break;

                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                            smsStatus = SmsStatusConstants.RADIO_OFF;
                            statusMessage = "Rádio desligado";
                            success = false;
                            Log.e("SMS", "📡 Rádio desligado: " + phoneNumber);
                            break;

                        default:
                            smsStatus = SmsStatusConstants.FAILED;
                            statusMessage = "Código de erro desconhecido: " + getResultCode();
                            success = false;
                            Log.e("SMS", "❌ Erro desconhecido: " + getResultCode());
                            break;
                    }

                    // Atualizar status no servidor
                    final String finalStatus = smsStatus;
                    final String finalMessage = statusMessage;
                    final boolean finalSuccess = success;

                    updateSmsStatus(patient.id, finalStatus, statusMessage, new StatusUpdateCallback() {
                        @Override
                        public void onStatusUpdated(boolean updateSuccess, String updateMessage) {
                            Log.d("SMS", "🔄 Status atualizado no servidor: " + finalStatus +
                                    " - " + finalMessage);

                            // Chamar callback
                            if (callback != null) {
                                callback.onSmsSent(phoneNumber, finalSuccess, patient.id, message);
                            }
                        }
                    });

                    // Desregistrar o receiver
                    try {
                        context.unregisterReceiver(this);
                    } catch (IllegalArgumentException e) {
                        // Receiver já não está registrado
                    }
                }
            };

            // Registrar BroadcastReceiver para entrega
            BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            // SMS entregue
                            Log.d("SMS", "📨 SMS entregue: " + phoneNumber);

                            updateSmsStatus(patient.id, SmsStatusConstants.DELIVERED, "SMS entregue ao destinatário",
                                    new StatusUpdateCallback() {
                                        @Override
                                        public void onStatusUpdated(boolean success, String updateMessage) {
                                            Log.d("SMS", "✅ Status atualizado: delivered");
                                            Toast.makeText(context, "📨 SMS entregue: " + phoneNumber,
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                            break;

                        case Activity.RESULT_CANCELED:
                            // SMS não entregue
                            Log.w("SMS", "📭 SMS não entregue: " + phoneNumber);
                            break;
                    }

                    // Desregistrar o receiver
                    try {
                        context.unregisterReceiver(this);
                    } catch (IllegalArgumentException e) {
                        // Receiver já não está registrado
                    }
                }
            };

            // Registrar os receivers
            IntentFilter sentFilter = new IntentFilter(sentAction);
            IntentFilter deliveredFilter = new IntentFilter(deliveredAction);

            // Usar registerReceiver com flags para versões mais recentes do Android
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.registerReceiver(sentReceiver, sentFilter, Context.RECEIVER_EXPORTED);
                context.registerReceiver(deliveredReceiver, deliveredFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(sentReceiver, sentFilter);
                context.registerReceiver(deliveredReceiver, deliveredFilter);
            }

            // Enviar SMS real
            try {
                // Dividir mensagem se for muito longa
                ArrayList<String> parts = smsManager.divideMessage(message);

                if (parts.size() > 1) {
                    // Mensagem longa (multipart)
                    Log.d("SMS", "📨 Mensagem longa (" + parts.size() + " partes)");

                    ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();

                    for (int i = 0; i < parts.size(); i++) {
                        sentIntents.add(sentPendingIntent);
                        deliveredIntents.add(deliveredPendingIntent);
                    }

                    smsManager.sendMultipartTextMessage(
                            phoneNumber,
                            null,
                            parts,
                            sentIntents,
                            deliveredIntents
                    );

                    Log.d("SMS", "📤 SMS multipart enviado: " + parts.size() + " partes");

                } else {
                    // Mensagem curta (single part)
                    smsManager.sendTextMessage(
                            phoneNumber,
                            null,
                            message,
                            sentPendingIntent,
                            deliveredPendingIntent
                    );

                    Log.d("SMS", "📤 SMS single part enviado");
                }

            } catch (Exception e) {
                Log.e("SMS", "❌ Erro ao enviar SMS: " + e.getMessage());

                // Atualizar status como falha
                updateSmsStatus(patient.id, SmsStatusConstants.FAILED, "Erro: " + e.getMessage(),
                        new StatusUpdateCallback() {
                            @Override
                            public void onStatusUpdated(boolean success, String updateMessage) {
                                Log.e("SMS", "❌ Erro registrado no servidor");
                                callback.onSmsSent(phoneNumber, false, patient.id, message);
                            }
                        });

                Toast.makeText(context,
                        "❌ Erro ao enviar: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("SMS", "❌ Erro no processo de envio: " + e.getMessage());
            callback.onSmsSent(patient.contact, false, patient.id, patient.getFormattedMessage());

            // Tentar atualizar status de erro
            updateSmsStatus(patient.id, SmsStatusConstants.FAILED, "Erro no processo: " + e.getMessage(),
                    new StatusUpdateCallback() {
                        @Override
                        public void onStatusUpdated(boolean success, String updateMessage) {
                            Log.e("SMS", "❌ Erro de processo registrado");
                        }
                    });
        }
    }

    // Método para atualizar status no servidor
    public void updateSmsStatus(int patientId, String smsStatus, String statusMessage, final StatusUpdateCallback callback) {
        String url = BASE_URL + "/api/observation/smsstatus/" + patientId;
        Log.d("API_STATUS", "🔄 Atualizando SMS status para ID " + patientId +
                ": status=" + smsStatus + ", mensagem=" + statusMessage);

        StringRequest stringRequest = new StringRequest(Request.Method.PUT, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            String message = jsonResponse.optString("message", "Status atualizado");
                            Log.d("API_STATUS", "✅ Status atualizado: " + message);
                            callback.onStatusUpdated(true, message);
                        } catch (JSONException e) {
                            Log.w("API_STATUS", "⚠️ Resposta não é JSON válido: " + response);
                            callback.onStatusUpdated(true, "Status atualizado (resposta inválida)");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getVolleyErrorMessage(error);
                        Log.e("API_STATUS", "❌ Erro ao atualizar status: " + errorMsg);
                        callback.onStatusUpdated(false, "Falha: " + errorMsg);
                    }
                }) {
            @Override
            public byte[] getBody() {
                try {
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("smsStatus", smsStatus);  // Campo correto para status de SMS
                    jsonBody.put("statusMessage", statusMessage);  // Mensagem detalhada
                    jsonBody.put("timestamp", System.currentTimeMillis());  // Timestamp
                    return jsonBody.toString().getBytes("utf-8");
                } catch (Exception e) {
                    Log.e("API_STATUS", "❌ Erro ao criar corpo da requisição", e);
                    return null;
                }
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000, // 10 segundos
                2,     // 2 tentativas
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(stringRequest);
    }

    // Enviar SMS para todos os pacientes com atualização de status e feedback
    public void sendBulkSMS(List<Patient> patients, final SmsCallback callback) {
        if (patients == null || patients.isEmpty()) {
            Log.w("SMS", "📭 Nenhum paciente para enviar SMS");
            Toast.makeText(context, "📭 Nenhum paciente para enviar SMS", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("SMS", "📤 INICIANDO ENVIO EM MASSA PARA " + patients.size() + " PACIENTES");
        Toast.makeText(context,
                "📤 Iniciando envio para " + patients.size() + " pacientes",
                Toast.LENGTH_LONG).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < patients.size(); i++) {
                    Patient patient = patients.get(i);

                    // Log de progresso
                    Log.d("SMS", "─────────────────────────────────────────");
                    Log.d("SMS", "📊 PROGRESSO: " + (i + 1) + "/" + patients.size());
                    Log.d("SMS", "─────────────────────────────────────────");

                    // Notificar sobre o progresso
                    if (callback != null) {
                        callback.onSmsProgress(
                                patient.contact,
                                patient.getFormattedMessage(),
                                patient.id,
                                i + 1,
                                patients.size()
                        );
                    }

                    // Enviar SMS
                    sendSMS(patient, new SmsCallback() {
                        @Override
                        public void onSmsSent(String phone, boolean success, int patientId, String message) {
                            // Chamar o callback original
                            if (callback != null) {
                                callback.onSmsSent(phone, success, patientId, message);
                            }
                        }

                        @Override
                        public void onSmsProgress(String phone, String message, int patientId, int progress, int total) {
                            // Propagação do progresso
                            if (callback != null) {
                                callback.onSmsProgress(phone, message, patientId, progress, total);
                            }
                        }
                    });

                    // Delay entre envios para não sobrecarregar
                    try {
                        Thread.sleep(2000); // 2 segundos entre SMS (pode ajustar)
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                Log.d("SMS", "✅ ENVIO EM MASSA CONCLUÍDO");
                Toast.makeText(context,
                        "✅ Envio em massa concluído!",
                        Toast.LENGTH_LONG).show();
            }
        }).start();
    }

    // Método para obter mensagem de erro detalhada do Volley
    private String getVolleyErrorMessage(VolleyError error) {
        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            try {
                String data = new String(error.networkResponse.data, "UTF-8");
                return "HTTP " + statusCode + ": " + data;
            } catch (Exception e) {
                return "HTTP " + statusCode + ": [dados binários]";
            }
        } else if (error.getMessage() != null) {
            return error.getMessage();
        } else if (error.getCause() != null) {
            return error.getCause().getMessage();
        } else {
            return "Erro desconhecido";
        }
    }

    // Formatar número de telefone
    private String formatPhoneNumber(String contact) {
        if (contact == null || contact.trim().isEmpty()) {
            return "";
        }

        // Remover espaços e caracteres especiais
        String phone = contact.replaceAll("[^\\d]", "");

        // Verificar se é número moçambicano válido
        if (phone.length() == 9) {
            String[] validPrefixes = {"82", "83", "84", "85", "86", "87", "88", "89"};
            for (String prefix : validPrefixes) {
                if (phone.startsWith(prefix)) {
                    return "+258" + phone;
                }
            }
        } else if (phone.length() == 12 && phone.startsWith("258")) {
            return "+" + phone;
        }

        Log.w("SMS", "📵 Número inválido formatado: " + contact + " -> " + phone);
        return "";
    }

    // Método auxiliar para log detalhado
    public void logPatientDetails(Patient patient) {
        Log.d("PATIENT_DETAILS", "═══════════════════════════════════════");
        Log.d("PATIENT_DETAILS", "👤 PACIENTE DETALHADO:");
        Log.d("PATIENT_DETAILS", "   🆔 ID: " + patient.id);
        Log.d("PATIENT_DETAILS", "   📛 Nome: " + patient.fullname);
        Log.d("PATIENT_DETAILS", "   📞 Telefone: " + patient.contact);
        Log.d("PATIENT_DETAILS", "   ⚤ Género: " + patient.gender);
        Log.d("PATIENT_DETAILS", "   📝 Mensagem: " + patient.textMessageDescription);
        Log.d("PATIENT_DETAILS", "   📊 Estado: " + patient.stateDescription);
        Log.d("PATIENT_DETAILS", "═══════════════════════════════════════");
    }

    // Método para testar envio individual com feedback
    public void testSendSMS(Patient patient) {
        String phoneNumber = formatPhoneNumber(patient.contact);
        String message = patient.getFormattedMessage();

        if (phoneNumber.isEmpty()) {
            Toast.makeText(context,
                    "❌ Número inválido: " + patient.contact,
                    Toast.LENGTH_LONG).show();
            return;
        }

        String displayMessage = "📤 TESTE DE ENVIO:\n" +
                "Para: " + phoneNumber + "\n" +
                "Mensagem: " +
                (message.length() > 100 ? message.substring(0, 100) + "..." : message);

        Toast.makeText(context, displayMessage, Toast.LENGTH_LONG).show();
        Log.d("SMS_TEST", displayMessage);
    }

    // Adicione estas classes dentro do ApiService
    public static class Appointment {
        public int id;
        public int patientId;
        public String patientName;
        public String phone;
        public String appointmentDate;
        public String appointmentTime;
        public String doctorName;
        public String appointmentType;
        public String status;
        public String observations;
        public String nid;  // Novo campo para NID

        public Appointment(int id, int patientId, String patientName, String phone,
                           String appointmentDate, String appointmentTime,
                           String doctorName, String appointmentType,
                           String status, String observations) {
            this(id, patientId, patientName, phone, appointmentDate, appointmentTime,
                    doctorName, appointmentType, status, observations, null);
        }

        public Appointment(int id, int patientId, String patientName, String phone,
                           String appointmentDate, String appointmentTime,
                           String doctorName, String appointmentType,
                           String status, String observations, String nid) {
            this.id = id;
            this.patientId = patientId;
            this.patientName = patientName;
            this.phone = phone;
            this.appointmentDate = appointmentDate;
            this.appointmentTime = appointmentTime;
            this.doctorName = doctorName;
            this.appointmentType = appointmentType;
            this.status = status;
            this.observations = observations;
            this.nid = nid;
        }

        @Override
        public String toString() {
            return "Appointment{id=" + id + ", patient=" + patientName +
                    ", date=" + appointmentDate + " " + appointmentTime +
                    ", nid=" + (nid != null ? nid : "Não informado") + "}";
        }
    }

    public static class Doctor {
        public int id;
        public String name;
        public String specialty;
        public String availability;

        public Doctor(int id, String name, String specialty, String availability) {
            this.id = id;
            this.name = name;
            this.specialty = specialty;
            this.availability = availability;
        }
    }

    // Interface para callbacks de agendamento
    public interface AppointmentCallback {
        void onSuccess(Appointment appointment);
        void onError(String error);
    }

    public interface DoctorsCallback {
        void onSuccess(List<Doctor> doctors);
        void onError(String error);
    }

    public interface AppointmentsCallback {
        void onSuccess(List<Appointment> appointments);
        void onError(String error);
    }

    // Método para processar mensagem de agendamento
    public void processAppointmentSMS(String phone, String message, final AppointmentCallback callback) {
        String url = BASE_URL + "/api/appointments/process-sms";

        Log.d("APPOINTMENT_SMS", "📱 Processando SMS de agendamento");
        Log.d("APPOINTMENT_SMS", "📞 Telefone: " + phone);
        Log.d("APPOINTMENT_SMS", "📝 Mensagem: " + message);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d("APPOINTMENT_SMS", "✅ Resposta: " + response.toString());

                            if (response.getBoolean("success")) {
                                JSONObject appointmentJson = response.getJSONObject("appointment");

                                // Extrair NID se existir
                                String nid = appointmentJson.has("nid") ?
                                        appointmentJson.optString("nid", null) : null;

                                Appointment appointment = new Appointment(
                                        appointmentJson.getInt("id"),
                                        appointmentJson.optInt("patient_id", 0),
                                        appointmentJson.getString("patient_name"),
                                        appointmentJson.getString("telefone"),
                                        appointmentJson.getString("data_consulta"),
                                        appointmentJson.getString("hora_consulta"),
                                        appointmentJson.getString("medico_nome"),
                                        appointmentJson.getString("tipo_consulta"),
                                        appointmentJson.getString("status"),
                                        appointmentJson.optString("observacoes", ""),
                                        nid  // Passar o NID
                                );

                                callback.onSuccess(appointment);
                                Log.d("APPOINTMENT_SMS", "✅ Agendamento criado: " + appointment.toString());

                            } else {
                                String errorMsg = response.getString("mensagem");
                                Log.e("APPOINTMENT_SMS", "❌ Erro: " + errorMsg);
                                callback.onError(errorMsg);
                            }
                        } catch (JSONException e) {
                            Log.e("APPOINTMENT_SMS", "❌ Erro ao processar resposta", e);
                            callback.onError("Erro no formato da resposta: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getVolleyErrorMessage(error);
                        Log.e("APPOINTMENT_SMS", "💥 Erro na requisição: " + errorMsg);

                        // Log detalhado do erro
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                String body = new String(error.networkResponse.data, "UTF-8");
                                Log.e("APPOINTMENT_SMS", "📄 Corpo do erro: " + body);
                            } catch (Exception e) {
                                Log.e("APPOINTMENT_SMS", "❌ Não foi possível ler corpo do erro");
                            }
                        }

                        callback.onError("Falha na conexão: " + errorMsg);
                    }
                }) {
            @Override
            public byte[] getBody() {
                try {
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("phone", phone);
                    jsonBody.put("message", message);
                    jsonBody.put("timestamp", System.currentTimeMillis());

                    Log.d("APPOINTMENT_SMS", "📤 Enviando para " + url + ": " + jsonBody.toString());
                    return jsonBody.toString().getBytes("utf-8");
                } catch (Exception e) {
                    Log.e("APPOINTMENT_SMS", "❌ Erro ao criar corpo da requisição", e);
                    return null;
                }
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000, // 15 segundos
                2,     // 2 tentativas
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(jsonObjectRequest);
    }

    // Método para buscar médicos disponíveis
    public void getAvailableDoctors(String date, final DoctorsCallback callback) {
        String url = BASE_URL + "/api/agendamentos/medicos";

        Log.d("APPOINTMENT", "👨‍⚕️ Buscando médicos para: " + date);

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Doctor> doctors = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject doctorJson = response.getJSONObject(i);
                                Doctor doctor = new Doctor(
                                        doctorJson.getInt("id"),
                                        doctorJson.getString("name"),
                                        doctorJson.getString("specialty"),
                                        doctorJson.getString("availability")
                                );
                                doctors.add(doctor);
                            }
                            callback.onSuccess(doctors);
                        } catch (JSONException e) {
                            Log.e("APPOINTMENT", "❌ Erro ao parsear médicos", e);
                            callback.onError("Erro ao processar lista de médicos");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getVolleyErrorMessage(error);
                        Log.e("APPOINTMENT", "💥 Erro: " + errorMsg);
                        callback.onError("Falha na conexão: " + errorMsg);
                    }
                }
        );

        jsonArrayRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(jsonArrayRequest);
    }

    // Método para buscar todos os agendamentos
    public void getAllAppointments(final AppointmentsCallback callback) {
        String url = BASE_URL + "/api/agendamentos/listar";

        Log.d("APPOINTMENT", "📅 Buscando agendamentos: " + url);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d("APPOINTMENT", "📨 Resposta recebida");

                            // Verificar se tem o campo 'agendamentos'
                            if (response.has("agendamentos")) {
                                JSONArray agendamentosArray = response.getJSONArray("agendamentos");
                                List<Appointment> appointments = new ArrayList<>();

                                Log.d("APPOINTMENT", "📊 Encontrados " + agendamentosArray.length() + " agendamentos");

                                for (int i = 0; i < agendamentosArray.length(); i++) {
                                    JSONObject apptJson = agendamentosArray.getJSONObject(i);

                                    // Criar appointment - usar optString/optInt para evitar erros
                                    Appointment appointment = new Appointment(
                                            apptJson.optInt("id", 0),
                                            apptJson.optInt("paciente_id", 0),
                                            apptJson.optString("paciente_nome", "Não informado"),
                                            apptJson.optString("telefone", ""),
                                            apptJson.optString("data_consulta", ""),
                                            apptJson.optString("hora_consulta", ""),
                                            apptJson.optString("medico_nome", "A definir"),
                                            apptJson.optString("tipo_consulta", "Consulta"),
                                            apptJson.optString("status", "pendente"),
                                            apptJson.optString("observacoes", "")
                                    );
                                    appointments.add(appointment);
                                }

                                callback.onSuccess(appointments);
                            } else {
                                // Verificar se tem mensagem de erro
                                String errorMsg = "Resposta da API sem agendamentos";
                                if (response.has("error")) {
                                    errorMsg = response.getString("error");
                                } else if (response.has("mensagem")) {
                                    errorMsg = response.getString("mensagem");
                                }
                                Log.e("APPOINTMENT", "❌ " + errorMsg);
                                callback.onError(errorMsg);
                            }

                        } catch (JSONException e) {
                            Log.e("APPOINTMENT", "❌ Erro ao parsear JSON", e);
                            callback.onError("Erro no formato da resposta: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getVolleyErrorMessage(error);
                        Log.e("APPOINTMENT", "💥 Erro de rede: " + errorMsg);

                        // Log mais detalhado
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            try {
                                String body = new String(error.networkResponse.data, "UTF-8");
                                Log.e("APPOINTMENT", "📄 Corpo do erro: " + body);
                            } catch (Exception e) {
                                Log.e("APPOINTMENT", "❌ Não foi possível ler corpo do erro");
                            }
                        }

                        callback.onError("Erro de rede: " + errorMsg);
                    }
                }
        );

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000, // 10 segundos
                2,     // 2 tentativas
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(jsonObjectRequest);
    }

    // Método para atualizar status do agendamento
    public void updateAppointmentStatus(int appointmentId, String status, String doctorName, final StatusUpdateCallback callback) {
        String url = BASE_URL + "/api/agendamentos/" + appointmentId + "/status";

        Log.d("APPOINTMENT", "🔄 Atualizando status do agendamento " + appointmentId + " para: " + status);

        StringRequest stringRequest = new StringRequest(Request.Method.PUT, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            JSONObject jsonResponse = new JSONObject(response);
                            if (jsonResponse.getBoolean("success")) {
                                callback.onStatusUpdated(true, jsonResponse.getString("mensagem"));
                            } else {
                                callback.onStatusUpdated(false, jsonResponse.getString("mensagem"));
                            }
                        } catch (JSONException e) {
                            Log.e("APPOINTMENT", "❌ Erro ao processar resposta", e);
                            callback.onStatusUpdated(false, "Erro no formato da resposta");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = getVolleyErrorMessage(error);
                        Log.e("APPOINTMENT", "💥 Erro: " + errorMsg);
                        callback.onStatusUpdated(false, "Falha na conexão: " + errorMsg);
                    }
                }) {
            @Override
            public byte[] getBody() {
                try {
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("status", status);
                    jsonBody.put("doctor_name", doctorName);
                    jsonBody.put("updated_at", System.currentTimeMillis());
                    return jsonBody.toString().getBytes("utf-8");
                } catch (Exception e) {
                    Log.e("APPOINTMENT", "❌ Erro ao criar corpo da requisição", e);
                    return null;
                }
            }

            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }
        };

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(stringRequest);
    }
}

// Classe separada para constantes de status de SMS
class SmsStatusConstants {
    public static final String PENDING = "pending";
    public static final String SENT = "sent";
    public static final String DELIVERED = "delivered";
    public static final String FAILED = "failed";
    public static final String NO_SERVICE = "no_service";
    public static final String NULL_PDU = "null_pdu";
    public static final String RADIO_OFF = "radio_off";
}