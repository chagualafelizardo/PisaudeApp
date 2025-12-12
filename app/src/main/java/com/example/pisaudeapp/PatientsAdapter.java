package com.example.pisaudeapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PatientsAdapter extends RecyclerView.Adapter<PatientsAdapter.PatientViewHolder> {

    private List<ApiService.Patient> patients;
    private OnPatientClickListener listener;

    public interface OnPatientClickListener {
        void onPatientClick(ApiService.Patient patient);
        void onCallClick(ApiService.Patient patient);
    }

    public PatientsAdapter(List<ApiService.Patient> patients, OnPatientClickListener listener) {
        this.patients = patients;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        ApiService.Patient patient = patients.get(position);
        holder.bind(patient, listener);
    }

    @Override
    public int getItemCount() {
        return patients.size();
    }

    public void updatePatients(List<ApiService.Patient> newPatients) {
        patients.clear();
        patients.addAll(newPatients);
        notifyDataSetChanged();
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {
        private TextView tvName, tvPhone, tvStatus, tvGender;
        private Button btnCall;

        PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvPhone = itemView.findViewById(R.id.tv_phone);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvGender = itemView.findViewById(R.id.tv_gender);
            btnCall = itemView.findViewById(R.id.btn_call);
        }

        void bind(ApiService.Patient patient, OnPatientClickListener listener) {
            tvName.setText(patient.fullname != null ? patient.fullname : "Nome não informado");
            tvPhone.setText(patient.contact != null ? formatPhoneNumber(patient.contact) : "Sem telefone");

            // Exibir estado em português formatado
            tvStatus.setText(formatEstado(patient.stateDescription));

            tvGender.setText(patient.gender != null ? patient.gender : "Gênero não informado");

            // Configurar cores baseadas no status
            setStatusColor(patient.stateDescription);

            // Configurar botão de chamada
            if (patient.contact != null && !patient.contact.trim().isEmpty()) {
                btnCall.setVisibility(View.VISIBLE);
                btnCall.setOnClickListener(v -> listener.onCallClick(patient));
            } else {
                btnCall.setVisibility(View.GONE);
            }

            // Configurar clique no item inteiro
            itemView.setOnClickListener(v -> listener.onPatientClick(patient));
        }

        private String formatPhoneNumber(String phone) {
            if (phone == null) return "";
            String cleanPhone = phone.trim().replace("+", "").replace(" ", "");
            if (cleanPhone.length() >= 9) {
                return cleanPhone.substring(0, 3) + " " +
                        cleanPhone.substring(3, 6) + " " +
                        cleanPhone.substring(6);
            }
            return cleanPhone;
        }

        private String formatEstado(String estado) {
            if (estado == null) return "Estado não informado";

            // Converter para português formatado
            String estadoLower = estado.toLowerCase();
            if (estadoLower.contains("inicial")) {
                return "📋 Inicial";
            } else if (estadoLower.contains("faltoso")) {
                return "⚠️ Faltoso";
            } else if (estadoLower.contains("abandono")) {
                return "🚫 Abandono";
            } else if (estadoLower.contains("levantamento")) {
                return "📈 Levantamento";
            } else if (estadoLower.contains("chamada")) {
                return "📞 Chamada";
            } else if (estadoLower.contains("visita") || estadoLower.contains("domiciliar")) {
                return "🏠 Visita Domiciliar";
            } else {
                return estado;
            }
        }

        private void setStatusColor(String status) {
            if (status == null) return;

            int color;
            String statusLower = status.toLowerCase();

            if (statusLower.contains("inicial")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_blue_dark);
            } else if (statusLower.contains("faltoso")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_orange_dark);
            } else if (statusLower.contains("abandono")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_red_dark);
            } else if (statusLower.contains("levantamento")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_green_dark);
            } else if (statusLower.contains("chamada")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_purple);
            } else if (statusLower.contains("visita") || statusLower.contains("domiciliar")) {
                color = itemView.getContext().getResources().getColor(android.R.color.holo_red_light);
            } else {
                color = itemView.getContext().getResources().getColor(android.R.color.darker_gray);
            }

            tvStatus.setTextColor(color);
        }
    }
}