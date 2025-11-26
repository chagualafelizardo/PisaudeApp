package com.example.pisaudeapp;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ApiService {
    private Context context;
    private RequestQueue requestQueue;
    private SmsManager smsManager;

    // Interface para callback
    public interface ApiCallback {
        void onSuccess(List<Patient> patients);
        void onError(String error);
    }

    public interface SmsCallback {
        void onSmsSent(String phone, boolean success);
    }

    public ApiService(Context context) {
        this.context = context;
        this.requestQueue = Volley.newRequestQueue(context);
        this.smsManager = SmsManager.getDefault();
    }

    // Classe Patient simples
    public static class Patient {
        public String fullname;
        public String contact;
        public String gender;
        public String textMessageDescription;

        public Patient(String fullname, String contact, String gender, String textMessageDescription) {
            this.fullname = fullname;
            this.contact = contact;
            this.gender = gender;
            this.textMessageDescription = textMessageDescription;
        }

        public boolean isValid() {
            return contact != null && !contact.trim().isEmpty() &&
                    textMessageDescription != null && !textMessageDescription.trim().isEmpty();
        }

        public String getFormattedMessage() {
            String greeting = "F".equals(gender) ? "Prezada" : "Prezado";
            return greeting + " " + fullname + ", " + textMessageDescription;
        }
    }

    // Buscar pacientes da API
    public void getPatients(final ApiCallback callback) {
        String url = "http://localhost:5000/api/observation";

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Patient> patients = parsePatients(response);
                            callback.onSuccess(patients);
                        } catch (JSONException e) {
                            callback.onError("Erro ao processar dados: " + e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.onError("Erro de conexão: " + error.getMessage());
                    }
                }
        );

        requestQueue.add(jsonArrayRequest);
    }

    // Parse dos dados JSON
    private List<Patient> parsePatients(JSONArray jsonArray) throws JSONException {
        List<Patient> patients = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject patientJson = jsonArray.getJSONObject(i);

            String fullname = patientJson.getString("fullname");
            String contact = patientJson.getString("contact");
            String gender = patientJson.getString("gender");
            String textMessage = patientJson.getString("textMessageDescription");

            Patient patient = new Patient(fullname, contact, gender, textMessage);
            if (patient.isValid()) {
                patients.add(patient);
            }
        }

        return patients;
    }

    // Enviar SMS para um paciente
    public void sendSMS(Patient patient, final SmsCallback callback) {
        try {
            String phoneNumber = formatPhoneNumber(patient.contact);
            String message = patient.getFormattedMessage();

            if (phoneNumber.isEmpty()) {
                callback.onSmsSent(patient.contact, false);
                return;
            }

            // Enviar SMS
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);

            Log.d("SMS", "Enviado para: " + patient.contact + " - " + patient.fullname);
            callback.onSmsSent(patient.contact, true);

        } catch (Exception e) {
            Log.e("SMS", "Erro ao enviar: " + e.getMessage());
            callback.onSmsSent(patient.contact, false);
        }
    }

    // Enviar SMS para todos os pacientes
    public void sendBulkSMS(List<Patient> patients, final SmsCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (Patient patient : patients) {
                    sendSMS(patient, callback);

                    // Pequeno delay entre envios
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    // Formatar número de telefone
    private String formatPhoneNumber(String contact) {
        if (contact == null) return "";

        // Remover espaços e caracteres especiais
        String phone = contact.replaceAll("[^\\d]", "");

        // Verificar se é número moçambicano válido
        String[] validPrefixes = {"82", "83", "84", "85", "86", "87"};
        for (String prefix : validPrefixes) {
            if (phone.startsWith(prefix) && phone.length() == 9) {
                return "+258" + phone;
            }
        }

        return "";
    }

    // Mostrar toast na thread principal
    private void showToast(final String message) {
        new android.os.Handler(context.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}