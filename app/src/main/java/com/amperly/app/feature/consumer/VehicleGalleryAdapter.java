package com.amperly.app.feature.consumer;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amperly.app.R;
import com.amperly.app.ui.Car3DView;
import com.amperly.app.vehicle.VehicleSpecs;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cards for the debug validation gallery.
 *
 * <p>Each card renders a still copy of the fitted model with the dimension box switched on, then
 * prints the resolved numbers underneath. A licensed reference photo is shown at the side when
 * one exists at {@code assets/reference_photos/<make>-<model>.png|jpg|webp}; otherwise the slot
 * says so instead of pretending to have a comparison.
 */
public class VehicleGalleryAdapter extends RecyclerView.Adapter<VehicleGalleryAdapter.Holder> {

    private static final String PHOTO_DIR = "reference_photos/";
    private static final String[] PHOTO_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp"};

    private final List<VehicleSpecs> specs = new ArrayList<>();

    public void submit(List<VehicleSpecs> vehicles) {
        specs.clear();
        if (vehicles != null) {
            specs.addAll(vehicles);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vehicle_gallery, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(specs.get(position));
    }

    @Override
    public int getItemCount() {
        return specs.size();
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView body;
        private final Car3DView car;
        private final ImageView reference;
        private final TextView referencePlaceholder;
        private final TextView dimensions;
        private final TextView chassis;
        private final TextView tyre;
        private final TextView source;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.galleryTitleText);
            body = itemView.findViewById(R.id.galleryBodyText);
            car = itemView.findViewById(R.id.galleryCarView);
            reference = itemView.findViewById(R.id.galleryReferenceImage);
            referencePlaceholder = itemView.findViewById(R.id.galleryReferencePlaceholder);
            dimensions = itemView.findViewById(R.id.galleryDimensionsText);
            chassis = itemView.findViewById(R.id.galleryChassisText);
            tyre = itemView.findViewById(R.id.galleryTyreText);
            source = itemView.findViewById(R.id.gallerySourceText);
        }

        void bind(VehicleSpecs vehicle) {
            title.setText(vehicle.displayName());
            body.setText(vehicle.bodyType.getLabel());

            car.setInteractive(false);
            car.setAnimated(false);
            car.setAutoRotate(false);
            car.setDimensionOverlay(true);
            car.setChargePortHighlighted(false);
            car.setVehicle(vehicle);

            dimensions.setText(itemView.getContext().getString(
                    R.string.vehicle_gallery_dimensions,
                    vehicle.lengthMm, vehicle.widthMm, vehicle.heightMm));
            chassis.setText(itemView.getContext().getString(
                    R.string.vehicle_gallery_wheelbase,
                    vehicle.wheelbaseMm, vehicle.groundClearanceMm));
            tyre.setText(itemView.getContext().getString(
                    R.string.vehicle_gallery_tyre,
                    vehicle.tyreWidthMm, vehicle.tyreAspectPercent, vehicle.rimDiameterIn));

            String sourceLabel = itemView.getContext().getString(
                    R.string.vehicle_gallery_source,
                    vehicle.confidence.getLabel(), vehicle.source);
            if (vehicle.curated != null) {
                sourceLabel += " · " + itemView.getContext()
                        .getString(R.string.vehicle_gallery_curated);
            }
            source.setText(sourceLabel);

            bindReferencePhoto(vehicle);
        }

        private void bindReferencePhoto(VehicleSpecs vehicle) {
            String name = slug(vehicle.make + "-" + vehicle.model);
            Bitmap bitmap = loadReferencePhoto(name);
            if (bitmap != null) {
                reference.setImageBitmap(bitmap);
                reference.setVisibility(View.VISIBLE);
                reference.setContentDescription(itemView.getContext().getString(
                        R.string.vehicle_gallery_photo_description, vehicle.displayName()));
                referencePlaceholder.setVisibility(View.GONE);
            } else {
                reference.setImageDrawable(null);
                reference.setVisibility(View.GONE);
                referencePlaceholder.setText(itemView.getContext().getString(
                        R.string.vehicle_gallery_no_photo, name + ".png"));
                referencePlaceholder.setVisibility(View.VISIBLE);
            }
        }

        private Bitmap loadReferencePhoto(String name) {
            AssetManager assets = itemView.getContext().getAssets();
            for (String extension : PHOTO_EXTENSIONS) {
                try (InputStream stream = assets.open(PHOTO_DIR + name + extension)) {
                    return BitmapFactory.decodeStream(stream);
                } catch (IOException ignored) {
                    // Try the next supported extension.
                }
            }
            return null;
        }

        private static String slug(String value) {
            return value.toLowerCase(Locale.US)
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
        }
    }
}
