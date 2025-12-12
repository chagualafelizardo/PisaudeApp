package com.example.pisaudeapp.ui.model;

public class Patient {
    public String fullname;
    public String contact;
    public String gender;
    public String textMessageDescription;
    public String stateDescription; // NOVO CAMPO

    public Patient(String fullname, String contact, String gender, String textMessageDescription, String stateDescription) {
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
}