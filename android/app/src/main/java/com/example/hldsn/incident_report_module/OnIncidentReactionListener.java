package com.example.hldsn.incident_report_module;

public interface OnIncidentReactionListener {
    void onLikeClicked(IncidentModel incident, int position);
    void onDislikeClicked(IncidentModel incident, int position);
    void onCommentsClicked(IncidentModel incident, int position);
}
