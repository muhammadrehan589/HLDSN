package com.example.hldsn;

public interface OnIncidentReactionListener {
    void onLikeClicked(IncidentModel incident, int position);
    void onDislikeClicked(IncidentModel incident, int position);
    void onCommentsClicked(IncidentModel incident, int position);
}
