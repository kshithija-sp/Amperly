package com.amperly.app.feature.consumer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.vehicle.SpecResolver;

/**
 * Debug-only validation gallery: the twelve catalogue cars side by side, each fitted from its
 * real specs with the dimension box drawn on the mesh.
 *
 * <p>Open it from the profile menu on a debug build. It is deliberately not part of the role
 * navigation, and the reference-photo slot stays honest: with no licensed photo bundled, the
 * card says where to add one.
 */
public class VehicleGalleryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vehicle_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recycler = view.findViewById(R.id.galleryRecyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        VehicleGalleryAdapter adapter = new VehicleGalleryAdapter();
        recycler.setAdapter(adapter);
        adapter.submit(SpecResolver.getInstance(requireContext()).catalogue());
    }
}
