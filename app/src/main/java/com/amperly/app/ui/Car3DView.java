package com.amperly.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amperly.app.vehicle.CarModelFitter;
import com.amperly.app.vehicle.VehicleGeometry;
import com.amperly.app.vehicle.VehicleSpecs;

import java.util.Arrays;
import java.util.Comparator;

/**
 * CarScene: draws the resolved vehicle as a shaded 3D model that floats above a perspective
 * turntable and turns slowly on it.
 *
 * <p>The model is rebuilt by {@link CarMesh} from {@link CarModelFitter} geometry, so it uses
 * the driver's real dimensions, tyre size and ride height. Paint comes from the vehicle's
 * colour list, and the charge flap pulses while the car is in a charging flow. Drag to orbit,
 * pinch to zoom, and {@link #resetView()} returns to the three-quarter showcase angle.
 */
public class Car3DView extends View {

    // Scene palette. The turntable glow is the charging green from the product brief.
    private static final int SCENE_ACCENT = 0xA3E635;

    // Camera: sits above and in front of the turntable, looking down at the car.
    private static final float FOCAL = 12f;
    private static final float CAMERA_PITCH = -0.22f;
    private static final float CAMERA_Y = 0.72f;
    private static final float START_YAW = 2.45f;
    private static final float YAW_SPEED = 0.42f;
    // The car hovers above the turntable and breathes around this resting height.
    private static final float BOB_BASE = 0.1f;
    private static final float BOB_AMPLITUDE = 0.05f;

    // Direction towards the key light, expressed in camera space (upper front left).
    private static final float LIGHT_X = -0.41f;
    private static final float LIGHT_Y = 0.717f;
    private static final float LIGHT_Z = -0.564f;
    // Half vector between the key light and the view direction (0, 0, -1).
    private static final float HALF_X = -0.23f;
    private static final float HALF_Y = 0.40f;
    private static final float HALF_Z = -0.88f;

    private static final Comparator<RenderFace> FAR_TO_NEAR =
            (a, b) -> Float.compare(b.depth, a.depth);

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint platformPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint platformRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimensionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path platformPath = new Path();

    private final ScaleGestureDetector scaleDetector;

    private RenderFace[] mesh = new RenderFace[0];
    private VehicleSpecs specs;
    private VehicleGeometry geometry;

    private long startNanos = System.nanoTime();
    private boolean running;
    private boolean autoRotate = true;
    private boolean continuousAnimation = true;
    private boolean interactive = true;
    private boolean showDimensions;
    private boolean loading;
    private boolean chargePortHighlighted;

    private float yawOffset;
    private float pitchOffset;
    private float userZoom = 1f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean dragging;

    private float scale = 1f;
    private float shadowRadius = 1f;
    private float glowRadius = 1f;

    private float camX;
    private float camY;
    private float cosYaw;
    private float sinYaw;
    private float cosPitch;
    private float sinPitch;
    private float lift;

    private float projX;
    private float projY;

    public Car3DView(Context context) {
        this(context, null);
    }

    public Car3DView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        fillPaint.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setColor(Color.argb(120, 42, 53, 41));
        platformPaint.setStyle(Paint.Style.FILL);
        platformPaint.setColor(Color.argb(18, 163, 230, 53));
        platformRingPaint.setStyle(Paint.Style.STROKE);
        platformRingPaint.setStrokeWidth(1.4f);
        platformRingPaint.setColor(Color.argb(70, 163, 230, 53));
        pulsePaint.setStyle(Paint.Style.FILL);
        hintPaint.setColor(Color.rgb(147, 161, 143));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        dimensionPaint.setStyle(Paint.Style.STROKE);
        dimensionPaint.setStrokeWidth(1.1f);
        dimensionPaint.setColor(Color.argb(150, 163, 230, 53));
        badgePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badgePaint.setTextAlign(Paint.Align.CENTER);
        badgePaint.setColor(Color.rgb(241, 245, 234));
        badgePaint.setLetterSpacing(0.08f);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        userZoom = clamp(userZoom * detector.getScaleFactor(), 0.7f, 1.45f);
                        invalidate();
                        return true;
                    }
                });
        setClickable(true);
        setContentDescription("Rotating 3D model of your vehicle");
    }

    /** Binds the view to a resolved vehicle. The mesh is rebuilt from its real dimensions. */
    public void setVehicle(@Nullable VehicleSpecs vehicleSpecs) {
        this.specs = vehicleSpecs;
        if (vehicleSpecs == null) {
            geometry = null;
            mesh = new RenderFace[0];
            invalidate();
            return;
        }
        geometry = CarModelFitter.fit(vehicleSpecs);
        CarMesh.Face[] built = CarMesh.build(geometry);
        RenderFace[] rebuilt = new RenderFace[built.length];
        for (int i = 0; i < built.length; i++) {
            rebuilt[i] = new RenderFace(built[i]);
        }
        mesh = rebuilt;
        setContentDescription("Rotating 3D model of " + vehicleSpecs.displayName());
        invalidate();
    }

    public @Nullable VehicleSpecs getVehicle() {
        return specs;
    }

    public @Nullable VehicleGeometry getGeometry() {
        return geometry;
    }

    /** Highlights the charge flap with a slow pulse while the driver is charging. */
    public void setChargePortHighlighted(boolean highlighted) {
        if (chargePortHighlighted != highlighted) {
            chargePortHighlighted = highlighted;
            invalidate();
        }
    }

    /** The validation gallery renders a still pose per card instead of a dozen animations. */
    public void setAutoRotate(boolean enabled) {
        autoRotate = enabled;
        invalidate();
    }

    /**
     * Stops the frame loop. The view still redraws on touch and on {@link #setVehicle}, which is
     * what the validation gallery needs for a grid of twelve cars without twelve animators.
     */
    public void setAnimated(boolean enabled) {
        continuousAnimation = enabled;
        invalidate();
    }

    /** Debug overlay: the dimension bounding box labels for the validation gallery. */
    public void setDimensionOverlay(boolean enabled) {
        showDimensions = enabled;
        invalidate();
    }

    public void setLoading(boolean value) {
        if (loading != value) {
            loading = value;
            invalidate();
        }
    }

    /** Returns to the showcase angle after the driver has orbited the car. */
    public void resetView() {
        yawOffset = 0f;
        pitchOffset = 0f;
        userZoom = 1f;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startNanos = System.nanoTime();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float frameScale = scaleFor(w, h);
        shadowRadius = Math.max(1f, frameScale * 2.35f);
        glowRadius = Math.max(1f, frameScale * 1.85f);
        shadowPaint.setShader(new RadialGradient(0f, 0f, shadowRadius,
                new int[]{0x8C000000, 0x40000000, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(0f, 0f, glowRadius,
                new int[]{0x59A3E635, 0x22A3E635, 0x00A3E635},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    private void updateRunning() {
        boolean shouldRun = isAttachedToWindow()
                && getWindowVisibility() == VISIBLE
                && getVisibility() == VISIBLE
                && animationsEnabled();
        running = shouldRun;
        if (shouldRun) {
            postInvalidateOnAnimation();
        }
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /** Lets a scrolling parent own the gesture (used by the validation gallery grid). */
    public void setInteractive(boolean enabled) {
        interactive = enabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive) {
            return false;
        }
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                dragging = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && event.getPointerCount() == 1) {
                    yawOffset += (event.getX() - lastTouchX) * 0.012f;
                    pitchOffset = clamp(pitchOffset + (event.getY() - lastTouchY) * 0.006f,
                            -0.32f, 0.26f);
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        float time = (System.nanoTime() - startNanos) / 1_000_000_000f;
        if (!continuousAnimation || !animationsEnabled()) {
            // A stable three-quarter pose for still cards and reduced-motion users.
            time = 2.6f;
        }

        float yaw = START_YAW + (autoRotate ? time * YAW_SPEED : 0f) + yawOffset;
        lift = BOB_BASE + (float) Math.sin(time * 0.9f) * BOB_AMPLITUDE;
        updateCamera(width, height, yaw);

        drawTurntable(canvas);
        drawFloatShadow(canvas, time);

        if (mesh.length > 0) {
            drawCar(canvas);
        }
        if (chargePortHighlighted && geometry != null) {
            drawChargePulse(canvas, time);
        }
        if (showDimensions && geometry != null) {
            drawDimensions(canvas);
        }
        drawBadge(canvas);
        if (loading) {
            drawLoading(canvas, time, height);
        }

        if (running && continuousAnimation) {
            postInvalidateOnAnimation();
        }
    }

    private static float scaleFor(int width, int height) {
        return Math.min(width * 0.17f, height * 0.35f);
    }

    private void updateCamera(int width, int height, float yaw) {
        camX = width / 2f;
        camY = height * 0.45f;
        scale = scaleFor(width, height) * userZoom;
        cosYaw = (float) Math.cos(yaw);
        sinYaw = (float) Math.sin(yaw);
        float pitch = CAMERA_PITCH + pitchOffset;
        cosPitch = (float) Math.cos(pitch);
        sinPitch = (float) Math.sin(pitch);
    }

    // ------------------------------------------------------------------ scene

    private void drawTurntable(Canvas canvas) {
        float half = 3.1f;
        for (float x = -half; x <= half + 0.01f; x += 0.775f) {
            projectPoint(x, 0f, -half);
            float startX = projX;
            float startY = projY;
            projectPoint(x, 0f, half);
            canvas.drawLine(startX, startY, projX, projY, gridPaint);
        }
        for (float z = -half; z <= half + 0.01f; z += 0.775f) {
            projectPoint(-half, 0f, z);
            float startX = projX;
            float startY = projY;
            projectPoint(half, 0f, z);
            canvas.drawLine(startX, startY, projX, projY, gridPaint);
        }

        platformPath.reset();
        int segments = 72;
        float radius = 2.2f;
        for (int i = 0; i <= segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            projectPoint((float) Math.cos(angle) * radius, 0.005f,
                    (float) Math.sin(angle) * radius);
            if (i == 0) {
                platformPath.moveTo(projX, projY);
            } else {
                platformPath.lineTo(projX, projY);
            }
        }
        platformPath.close();
        canvas.drawPath(platformPath, platformPaint);
        canvas.drawPath(platformPath, platformRingPaint);
    }

    /** Ground shadow plus underglow, both softening as the car rises. */
    private void drawFloatShadow(Canvas canvas, float time) {
        if (shadowPaint.getShader() == null) {
            return;
        }
        float heightFactor = 0.6f + 0.4f
                * (1f - (lift - BOB_BASE + BOB_AMPLITUDE) / (BOB_AMPLITUDE * 2f));
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 1.3f);

        projectPoint(0f, 0.01f, 0f);
        canvas.save();
        canvas.translate(projX, projY);
        canvas.scale(1f, 0.34f);
        shadowPaint.setAlpha((int) (150 * heightFactor));
        canvas.drawCircle(0f, 0f, shadowRadius, shadowPaint);
        glowPaint.setAlpha((int) (70 * pulse));
        canvas.drawCircle(0f, 0f, glowRadius, glowPaint);
        canvas.restore();
    }

    private void drawChargePulse(Canvas canvas, float time) {
        float[] anchor = CarMesh.chargePortAnchor(geometry);
        projectPoint(anchor[0], anchor[1] + lift, anchor[2]);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2f);
        float outer = scale * (0.22f + 0.05f * pulse);
        pulsePaint.setColor(SCENE_ACCENT);
        pulsePaint.setAlpha((int) (35 + 45 * pulse));
        canvas.drawCircle(projX, projY, outer, pulsePaint);
        pulsePaint.setAlpha((int) (110 + 80 * pulse));
        canvas.drawCircle(projX, projY, outer * 0.38f, pulsePaint);
    }

    /** Dimension-box overlay used by the validation gallery to check the fit visually. */
    private void drawDimensions(Canvas canvas) {
        float h = geometry.height;
        float hl = geometry.length / 2f;
        float hw = geometry.width / 2f;
        float[] xs = {-hw, hw, hw, -hw, -hw, hw, hw, -hw};
        float[] ys = {0f, 0f, h, h, 0f, 0f, h, h};
        float[] zs = {hl, hl, hl, hl, -hl, -hl, -hl, -hl};
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float[] sx = new float[8];
        float[] sy = new float[8];
        for (int i = 0; i < 8; i++) {
            projectPoint(xs[i], ys[i] + lift, zs[i]);
            sx[i] = projX;
            sy[i] = projY;
        }
        for (int[] edge : edges) {
            canvas.drawLine(sx[edge[0]], sy[edge[0]], sx[edge[1]], sy[edge[1]],
                    dimensionPaint);
        }

        float textSize = Math.max(8f, getHeight() * 0.038f);
        dimensionPaint.setStyle(Paint.Style.FILL);
        dimensionPaint.setTextSize(textSize);
        dimensionPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(geometry.lengthLabel(), sx[0], sy[0] + textSize + 2f, dimensionPaint);
        canvas.drawText(geometry.widthLabel(), sx[1], sy[1] + textSize + 2f, dimensionPaint);
        canvas.drawText(geometry.heightLabel(), sx[2] + 24f, sy[2] - 4f, dimensionPaint);
        dimensionPaint.setStyle(Paint.Style.STROKE);
    }

    /** Plain-text tailgate badge from the curated profile, drawn only when the rear faces us. */
    private void drawBadge(Canvas canvas) {
        if (specs == null || specs.curated == null || specs.curated.badge.isEmpty()
                || geometry == null) {
            return;
        }
        // The tailgate faces the camera around yaw 0; fade the badge out behind the car.
        float facing = cosYaw;
        if (facing < 0.3f) {
            return;
        }
        projectPoint(0f, geometry.beltline * 0.55f + lift, -geometry.length / 2f - 0.005f);
        badgePaint.setTextSize(clamp(scale * 0.075f, 7f, 15f));
        badgePaint.setAlpha((int) (210 * Math.min(1f, facing)));
        canvas.drawText(specs.curated.badge, projX, projY, badgePaint);
    }

    private void drawLoading(Canvas canvas, float time, int height) {
        hintPaint.setTextSize(Math.max(24f, height * 0.075f));
        hintPaint.setAlpha((int) (140 + 70 * Math.sin(time * 2.1f)));
        canvas.drawText("Resolving vehicle model…", getWidth() / 2f, height * 0.55f, hintPaint);
        hintPaint.setAlpha(255);
    }

    // ------------------------------------------------------------------ model rendering

    private void drawCar(Canvas canvas) {
        for (RenderFace face : mesh) {
            projectFace(face);
            face.shaded = shade(face);
        }
        Arrays.sort(mesh, 0, mesh.length, FAR_TO_NEAR);

        for (RenderFace face : mesh) {
            Path path = face.path;
            path.reset();
            path.moveTo(face.sx[0], face.sy[0]);
            int count = face.face.count;
            for (int i = 1; i < count; i++) {
                path.lineTo(face.sx[i], face.sy[i]);
            }
            path.close();
            fillPaint.setColor(face.shaded);
            canvas.drawPath(path, fillPaint);
        }
    }

    private void projectFace(RenderFace renderFace) {
        CarMesh.Face face = renderFace.face;
        float depth = 0f;
        for (int i = 0; i < face.count; i++) {
            float x = face.x[i];
            float y = face.y[i] + lift;
            float z = face.z[i];

            float rotatedX = x * cosYaw + z * sinYaw;
            float rotatedZ = -x * sinYaw + z * cosYaw;
            float pitchedY = y * cosPitch - rotatedZ * sinPitch;
            float pitchedZ = y * sinPitch + rotatedZ * cosPitch;
            float persp = FOCAL / (FOCAL + pitchedZ);

            renderFace.px[i] = rotatedX;
            renderFace.py[i] = pitchedY;
            renderFace.pz[i] = pitchedZ;
            renderFace.sx[i] = camX + rotatedX * scale * persp;
            renderFace.sy[i] = camY - (pitchedY - CAMERA_Y) * scale * persp;
            depth += pitchedZ;
        }
        renderFace.depth = depth / face.count;
    }

    private static int shade(RenderFace renderFace) {
        CarMesh.Face face = renderFace.face;
        if (face.material == CarMesh.MAT_EMISSIVE) {
            return face.color;
        }

        float ax = renderFace.px[1] - renderFace.px[0];
        float ay = renderFace.py[1] - renderFace.py[0];
        float az = renderFace.pz[1] - renderFace.pz[0];
        float bx = renderFace.px[2] - renderFace.px[0];
        float by = renderFace.py[2] - renderFace.py[0];
        float bz = renderFace.pz[2] - renderFace.pz[0];

        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-4f) {
            return face.color;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        if (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z < 0f) {
            // Light the back side too; it is only ever visible on open silhouettes.
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }

        float lambert = Math.max(0f, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        float specularPower;
        float ambient;
        float diffuse;
        float specularScale;

        switch (face.material) {
            case CarMesh.MAT_GLASS:
                ambient = 0.22f;
                diffuse = 0.62f;
                specularPower = 52f;
                specularScale = 0.9f;
                break;
            case CarMesh.MAT_TIRE:
                ambient = 0.5f;
                diffuse = 0.5f;
                specularPower = 0f;
                specularScale = 0f;
                break;
            case CarMesh.MAT_RIM:
                ambient = 0.35f;
                diffuse = 0.65f;
                specularPower = 20f;
                specularScale = 0.45f;
                break;
            case CarMesh.MAT_BODY:
            default:
                ambient = 0.26f;
                diffuse = 0.74f;
                specularPower = 34f;
                specularScale = 0.5f;
                break;
        }

        float specular = 0f;
        if (specularScale > 0f) {
            specular = (float) Math.pow(Math.max(0f,
                    nx * HALF_X + ny * HALF_Y + nz * HALF_Z), specularPower) * specularScale;
        }

        float lum = ambient + diffuse * lambert;
        int r = Math.min(255, Math.round(Color.red(face.color) * lum + specular * 240f));
        int g = Math.min(255, Math.round(Color.green(face.color) * lum + specular * 240f));
        int b = Math.min(255, Math.round(Color.blue(face.color) * lum + specular * 240f));
        return Color.rgb(r, g, b);
    }

    private void projectPoint(float x, float y, float z) {
        float rotatedX = x * cosYaw + z * sinYaw;
        float rotatedZ = -x * sinYaw + z * cosYaw;
        float pitchedY = y * cosPitch - rotatedZ * sinPitch;
        float pitchedZ = y * sinPitch + rotatedZ * cosPitch;
        float persp = FOCAL / (FOCAL + pitchedZ);
        projX = camX + rotatedX * scale * persp;
        projY = camY - (pitchedY - CAMERA_Y) * scale * persp;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RenderFace {
        final CarMesh.Face face;
        final float[] px = new float[4];
        final float[] py = new float[4];
        final float[] pz = new float[4];
        final float[] sx = new float[4];
        final float[] sy = new float[4];
        final Path path = new Path();

        int shaded;
        float depth;

        RenderFace(CarMesh.Face face) {
            this.face = face;
        }
    }
}
