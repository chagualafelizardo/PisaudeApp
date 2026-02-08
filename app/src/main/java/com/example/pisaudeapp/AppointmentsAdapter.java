package com.example.pisaudeapp;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppointmentsAdapter extends ArrayAdapter<ApiService.Appointment> {

    private Context context;
    private List<ApiService.Appointment> appointments;
    private LayoutInflater inflater;

    public AppointmentsAdapter(Context context, List<ApiService.Appointment> appointments) {
        super(context, R.layout.item_appointment, appointments);
        this.context = context;
        this.appointments = appointments;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return appointments.size();
    }

    @Override
    public ApiService.Appointment getItem(int position) {
        return appointments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return appointments.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_appointment, parent, false);
            holder = new ViewHolder();

            holder.tvPatientName = convertView.findViewById(R.id.tvPatientName);
            holder.tvDateTime = convertView.findViewById(R.id.tvDateTime);
            holder.tvDoctor = convertView.findViewById(R.id.tvDoctor);
            holder.tvStatus = convertView.findViewById(R.id.tvStatus);
            holder.tvPhone = convertView.findViewById(R.id.tvPhone);
            holder.tvNid = convertView.findViewById(R.id.tvNid); // Novo TextView para NID

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ApiService.Appointment appointment = appointments.get(position);

        // Formatar data para exibição amigável
        String displayDate = appointment.appointmentDate;
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(appointment.appointmentDate);
            displayDate = outputFormat.format(date);
        } catch (ParseException e) {
            // Usar formato original se não conseguir parsear
        }

        holder.tvPatientName.setText(appointment.patientName);
        holder.tvDateTime.setText(displayDate + " " + appointment.appointmentTime);
        holder.tvDoctor.setText(appointment.doctorName);
        holder.tvStatus.setText(appointment.status.toUpperCase());
        holder.tvPhone.setText(appointment.phone);

        // Mostrar NID se existir
        if (appointment.nid != null && !appointment.nid.isEmpty()) {
            holder.tvNid.setVisibility(View.VISIBLE);
            holder.tvNid.setText("NID: " + appointment.nid);
        } else {
            holder.tvNid.setVisibility(View.GONE);
        }

        // Colorir status
        switch (appointment.status.toLowerCase()) {
            case "pendente":
                holder.tvStatus.setBackgroundColor(Color.parseColor("#FFA500")); // Laranja
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            case "confirmado":
                holder.tvStatus.setBackgroundColor(Color.parseColor("#4CAF50")); // Verde
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            case "cancelado":
                holder.tvStatus.setBackgroundColor(Color.parseColor("#F44336")); // Vermelho
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            case "médico_atribuído":
                holder.tvStatus.setBackgroundColor(Color.parseColor("#2196F3")); // Azul
                holder.tvStatus.setTextColor(Color.WHITE);
                break;
            default:
                holder.tvStatus.setBackgroundColor(Color.parseColor("#9E9E9E")); // Cinza
                holder.tvStatus.setTextColor(Color.WHITE);
        }

        // Adicionar padding ao status
        holder.tvStatus.setPadding(8, 4, 8, 4);

        return convertView;
    }

    static class ViewHolder {
        TextView tvPatientName;
        TextView tvDateTime;
        TextView tvDoctor;
        TextView tvStatus;
        TextView tvPhone;
        TextView tvNid; // Novo campo para NID
    }
}