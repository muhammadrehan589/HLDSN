package com.example.hldsn.incident_report_module;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hldsn.R;

import java.util.ArrayList;
import java.util.List;

public class NearbyUsersFragment extends Fragment {

    private ChatListAdapter adapter;
    private TextView emptyState;
    private List<ChatUser> latestUsers = new ArrayList<>();
    private String latestQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_users_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.chatListRecyclerView);
        emptyState = view.findViewById(R.id.chatsEmptyState);
        emptyState.setText("No nearby users");

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatListAdapter(requireContext(), new ArrayList<>(), this::openConversation);
        recyclerView.setAdapter(adapter);

        ChatsViewModel viewModel = new ViewModelProvider(requireActivity()).get(ChatsViewModel.class);
        viewModel.getNearbyUsers().observe(getViewLifecycleOwner(), users -> {
            latestUsers = users == null ? new ArrayList<>() : users;
            render();
        });
        viewModel.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
            latestQuery = query == null ? "" : query;
            render();
        });
    }

    private void render() {
        List<ChatUser> filtered = filterUsers(latestUsers, latestQuery);
        adapter.updateList(filtered);
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<ChatUser> filterUsers(List<ChatUser> source, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }

        String normalized = query.trim().toLowerCase();
        List<ChatUser> filtered = new ArrayList<>();
        for (ChatUser user : source) {
            String name = user != null ? user.getName() : null;
            if (name != null && name.toLowerCase().contains(normalized)) {
                filtered.add(user);
            }
        }
        return filtered;
    }

    private void openConversation(ChatUser user) {
        if (user == null || getContext() == null) {
            return;
        }

        Intent intent = new Intent(requireContext(), ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_USER_ID, user.getUid());
        intent.putExtra(ConversationActivity.EXTRA_USER_NAME, user.getName());
        intent.putExtra(ConversationActivity.EXTRA_MESH_USER_ID, user.getMeshUserId());
        intent.putExtra(ConversationActivity.EXTRA_MESH_PUBLIC_KEY, user.getMeshPublicKey());
        intent.putExtra(ConversationActivity.EXTRA_MESH_DEVICE_NAME, user.getMeshDeviceName());
        startActivity(intent);
    }
}

