package com.example.pisaudeapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppointmentsActivity extends AppCompatActivity {

    private static final String TAG = "AppointmentsActivity";

    private ApiService apiService;
    private ListView listViewAppointments;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private Button btnRefresh;
    private Spinner spinnerFilter;

    private List<ApiService.Appointment> appointmentsList;
    private AppointmentsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointments);

        setTitle("📅 Agendamentos");

        // Inicializar API
        apiService = new ApiService(this);

        // Inicializar views
        listViewAppointments = findViewById(R.id.listViewAppointments);
        progressBar = findViewById(R.id.progressBarAppointments);
        tvStatus = findViewById(R.id.tvStatus);
        btnRefresh = findViewById(R.id.btnRefresh);
        spinnerFilter = findViewById(R.id.spinnerFilter);

        // Configurar spinner de filtro
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Todos", "Pendentes", "Confirmados", "Cancelados", "Hoje"}
        );
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(filterAdapter);

        // Configurar adapter para a lista
        appointmentsList = new ArrayList<>();
        adapter = new AppointmentsAdapter(this, appointmentsList);
        listViewAppointments.setAdapter(adapter);

        // Configurar click listener para atualizar status
        listViewAppointments.setOnItemClickListener((parent, view, position, id) -> {
            ApiService.Appointment appointment = appointmentsList.get(position);
            showAppointmentActionsDialog(appointment);
        });

        // Botão refresh
        btnRefresh.setOnClickListener(v -> loadAppointments());

        // Carregar agendamentos
        loadAppointments();
    }

    private void loadAppointments() {
        showLoading(true);
        tvStatus.setText("Carregando agendamentos...");

        apiService.getAllAppointments(new ApiService.AppointmentsCallback() {
            @Override
            public void onSuccess(List<ApiService.Appointment> appointments) {
                runOnUiThread(() -> {
                    showLoading(false);
                    appointmentsList.clear();

                    // Aplicar filtro
                    String filter = spinnerFilter.getSelectedItem().toString();
                    List<ApiService.Appointment> filteredAppointments = filterAppointments(appointments, filter);

                    appointmentsList.addAll(filteredAppointments);
                    adapter.notifyDataSetChanged();

                    tvStatus.setText(appointmentsList.size() + " agendamentos encontrados");

                    if (appointmentsList.isEmpty()) {
                        tvStatus.setText("Nenhum agendamento encontrado");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    tvStatus.setText("Erro: " + error);
                    Toast.makeText(AppointmentsActivity.this,
                            "Erro ao carregar agendamentos: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<ApiService.Appointment> filterAppointments(List<ApiService.Appointment> appointments, String filter) {
        List<ApiService.Appointment> filtered = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (ApiService.Appointment appt : appointments) {
            switch (filter) {
                case "Todos":
                    filtered.add(appt);
                    break;
                case "Pendentes":
                    if ("pendente".equalsIgnoreCase(appt.status)) {
                        filtered.add(appt);
                    }
                    break;
                case "Confirmados":
                    if ("confirmado".equalsIgnoreCase(appt.status)) {
                        filtered.add(appt);
                    }
                    break;
                case "Cancelados":
                    if ("cancelado".equalsIgnoreCase(appt.status)) {
                        filtered.add(appt);
                    }
                    break;
                case "Hoje":
                    if (today.equals(appt.appointmentDate)) {
                        filtered.add(appt);
                    }
                    break;
            }
        }

        return filtered;
    }

    private void showAppointmentActionsDialog(ApiService.Appointment appointment) {
        String[] actions = {"Confirmar", "Atribuir Médico", "Cancelar", "Ver Detalhes"};

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Ações para Agendamento")
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Confirmar
                            confirmAppointment(appointment);
                            break;
                        case 1: // Atribuir Médico
                            assignDoctor(appointment);
                            break;
                        case 2: // Cancelar
                            cancelAppointment(appointment);
                            break;
                        case 3: // Ver Detalhes
                            showAppointmentDetails(appointment);
                            break;
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void confirmAppointment(ApiService.Appointment appointment) {
        apiService.updateAppointmentStatus(appointment.id, "confirmado", appointment.doctorName,
                new ApiService.StatusUpdateCallback() {
                    @Override
                    public void onStatusUpdated(boolean success, String message) {
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(AppointmentsActivity.this,
                                        "✅ Agendamento confirmado", Toast.LENGTH_SHORT).show();
                                loadAppointments();

                                // Enviar SMS de confirmação
                                sendConfirmationSMS(appointment);
                            } else {
                                Toast.makeText(AppointmentsActivity.this,
                                        "❌ Erro: " + message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void assignDoctor(ApiService.Appointment appointment) {
        // Buscar médicos disponíveis para a data do agendamento
        showLoading(true);
        apiService.getAvailableDoctors(appointment.appointmentDate, new ApiService.DoctorsCallback() {
            @Override
            public void onSuccess(List<ApiService.Doctor> doctors) {
                runOnUiThread(() -> {
                    showLoading(false);
                    if (doctors.isEmpty()) {
                        Toast.makeText(AppointmentsActivity.this,
                                "Nenhum médico disponível nesta data", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Criar lista de nomes de médicos
                    String[] doctorNames = new String[doctors.size()];
                    for (int i = 0; i < doctors.size(); i++) {
                        doctorNames[i] = doctors.get(i).name + " - " + doctors.get(i).specialty;
                    }

                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(AppointmentsActivity.this);
                    builder.setTitle("Selecionar Médico")
                            .setItems(doctorNames, (dialog, which) -> {
                                ApiService.Doctor selectedDoctor = doctors.get(which);
                                updateAppointmentDoctor(appointment, selectedDoctor);
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(AppointmentsActivity.this,
                            "Erro ao buscar médicos: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateAppointmentDoctor(ApiService.Appointment appointment, ApiService.Doctor doctor) {
        apiService.updateAppointmentStatus(appointment.id, "médico_atribuído", doctor.name,
                new ApiService.StatusUpdateCallback() {
                    @Override
                    public void onStatusUpdated(boolean success, String message) {
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(AppointmentsActivity.this,
                                        "✅ Médico atribuído: " + doctor.name, Toast.LENGTH_SHORT).show();
                                loadAppointments();
                            } else {
                                Toast.makeText(AppointmentsActivity.this,
                                        "❌ Erro: " + message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void cancelAppointment(ApiService.Appointment appointment) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cancelar Agendamento")
                .setMessage("Deseja realmente cancelar este agendamento?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    apiService.updateAppointmentStatus(appointment.id, "cancelado", appointment.doctorName,
                            new ApiService.StatusUpdateCallback() {
                                @Override
                                public void onStatusUpdated(boolean success, String message) {
                                    runOnUiThread(() -> {
                                        if (success) {
                                            Toast.makeText(AppointmentsActivity.this,
                                                    "❌ Agendamento cancelado", Toast.LENGTH_SHORT).show();
                                            loadAppointments();
                                        }
                                    });
                                }
                            });
                })
                .setNegativeButton("Não", null)
                .show();
    }

    private void showAppointmentDetails(ApiService.Appointment appointment) {
        String details = "📋 Detalhes do Agendamento\n\n" +
                "👤 Paciente: " + appointment.patientName + "\n" +
                "📞 Telefone: " + appointment.phone + "\n" +
                "📅 Data: " + appointment.appointmentDate + "\n" +
                "⏰ Hora: " + appointment.appointmentTime + "\n" +
                "👨‍⚕️ Médico: " + appointment.doctorName + "\n" +
                "📝 Tipo: " + appointment.appointmentType + "\n" +
                "📊 Status: " + appointment.status + "\n" +
                "🗒️ Observações: " + appointment.observations;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Detalhes do Agendamento")
                .setMessage(details)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void sendConfirmationSMS(ApiService.Appointment appointment) {
        String message = "Olá " + appointment.patientName +
                ",\nSeu agendamento foi CONFIRMADO para " +
                appointment.appointmentDate + " às " + appointment.appointmentTime +
                ".\nMédico: " + appointment.doctorName +
                "\nPor favor, chegue 15 minutos antes.";

        // Criar paciente temporário para enviar SMS
        ApiService.Patient tempPatient = new ApiService.Patient(
                appointment.patientId,
                appointment.patientName,
                appointment.phone,
                "M", // Assume gênero masculino como default
                appointment.status,
                message
        );

        apiService.sendSMS(tempPatient, new ApiService.SmsCallback() {
            @Override
            public void onSmsSent(String phone, boolean success, int patientId, String message) {
                Log.d(TAG, "SMS de confirmação enviado: " + success);
            }

            @Override
            public void onSmsProgress(String phone, String message, int patientId, int progress, int total) {
                // Não usado
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRefresh.setEnabled(!show);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAppointments();
    }
}