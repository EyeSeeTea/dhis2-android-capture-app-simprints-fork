package org.dhis2.usescases.searchTrackEntity;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.databinding.BindingMethod;
import androidx.databinding.BindingMethods;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import org.dhis2.App;
import org.dhis2.Bindings.ExtensionsKt;
import org.dhis2.Bindings.ViewExtensionsKt;
import org.dhis2.R;
import org.dhis2.animations.CarouselViewAnimations;
import org.dhis2.data.forms.dataentry.ProgramAdapter;
import org.dhis2.data.forms.dataentry.fields.RowAction;
import org.dhis2.data.tuples.Trio;
import org.dhis2.databinding.ActivitySearchBinding;
import org.dhis2.uicomponents.map.carousel.CarouselAdapter;
import org.dhis2.uicomponents.map.geometry.FeatureExtensionsKt;
import org.dhis2.uicomponents.map.layer.MapLayerDialog;
import org.dhis2.uicomponents.map.managers.TeiMapManager;
import org.dhis2.uicomponents.map.mapper.MapRelationshipToRelationshipMapModel;
import org.dhis2.uicomponents.map.model.CarouselItemModel;
import org.dhis2.uicomponents.map.model.MapStyle;
import org.dhis2.usescases.coodinates.CoordinatesView;
import org.dhis2.usescases.enrollment.EnrollmentActivity;
import org.dhis2.usescases.general.ActivityGlobalAbstract;
import org.dhis2.usescases.orgunitselector.OUTreeActivity;
import org.dhis2.usescases.searchTrackEntity.adapters.FormAdapter;
import org.dhis2.usescases.searchTrackEntity.adapters.RelationshipLiveAdapter;
import org.dhis2.usescases.searchTrackEntity.adapters.SearchTeiLiveAdapter;
import org.dhis2.usescases.searchTrackEntity.adapters.SearchTeiModel;
import org.dhis2.usescases.teiDashboard.TeiDashboardMobileActivity;
import org.dhis2.utils.ColorUtils;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.HelpManager;
import org.dhis2.utils.NetworkUtils;
import org.dhis2.utils.customviews.ImageDetailBottomDialog;
import org.dhis2.utils.customviews.ScanTextView;
import org.dhis2.utils.filters.FilterItem;
import org.dhis2.utils.filters.FilterManager;
import org.dhis2.utils.filters.Filters;
import org.dhis2.utils.filters.FiltersAdapter;
import org.dhis2.utils.idlingresource.CountingIdlingResourceSingleton;
import org.hisp.dhis.android.core.arch.call.D2Progress;
import org.hisp.dhis.android.core.common.ValueTypeDeviceRendering;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttribute;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import kotlin.Pair;
import kotlin.Unit;
import timber.log.Timber;

import static org.dhis2.usescases.eventsWithoutRegistration.eventInitial.EventInitialPresenter.ACCESS_LOCATION_PERMISSION_REQUEST;
import static org.dhis2.utils.analytics.AnalyticsConstants.CHANGE_PROGRAM;
import static org.dhis2.utils.analytics.AnalyticsConstants.CLICK;
import static org.dhis2.utils.analytics.AnalyticsConstants.SHOW_HELP;

@BindingMethods({
        @BindingMethod(type = FloatingActionButton.class, attribute = "app:srcCompat", method = "setImageDrawable")
})
public class SearchTEActivity extends ActivityGlobalAbstract implements SearchTEContractsModule.View,
        MapboxMap.OnMapClickListener {

    ActivitySearchBinding binding;
    @Inject
    SearchTEContractsModule.Presenter presenter;
    @Inject
    CarouselViewAnimations animations;
    @Inject
    FiltersAdapter filtersAdapter;

    private String initialProgram;
    private String tEType;

    private boolean fromRelationship = false;
    private String fromRelationshipTeiUid;
    private boolean backDropActive;
    /**
     * 0 - it is general filter
     * 1 - it is search filter
     * 2 - it was closed
     */
    private int switchOpenClose = 2;

    ObservableBoolean needsSearch = new ObservableBoolean(true);

    private SearchTeiLiveAdapter liveAdapter;
    private RelationshipLiveAdapter relationshipLiveAdapter;
    private TeiMapManager teiMapManager;
    private boolean initSearchNeeded = true;
    private ObjectAnimator animation = null;
    private String updateTei;
    private String updateEvent;

    //---------------------------------------------------------------------------------------------

    //region LIFECYCLE
    @Override
    protected void onStart() {
        super.onStart();
        teiMapManager.onStart();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        tEType = getIntent().getStringExtra("TRACKED_ENTITY_UID");
        initialProgram = getIntent().getStringExtra("PROGRAM_UID");

        ((App) getApplicationContext()).userComponent().plus(new SearchTEModule(this, tEType, initialProgram)).inject(this);

        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_search);
        binding.setPresenter(presenter);
        binding.setNeedsSearch(needsSearch);
        binding.setTotalFilters(FilterManager.getInstance().getTotalFilters());
        binding.setTotalFiltersSearch(presenter.getQueryData().size());

        try {
            fromRelationship = getIntent().getBooleanExtra("FROM_RELATIONSHIP", false);
            fromRelationshipTeiUid = getIntent().getStringExtra("FROM_RELATIONSHIP_TEI");
        } catch (Exception e) {
            Timber.d(e.getMessage());
        }

        ViewExtensionsKt.clipWithRoundedCorners(binding.mainLayout, ExtensionsKt.getDp(16));
        if (fromRelationship) {
            relationshipLiveAdapter = new RelationshipLiveAdapter(presenter, getSupportFragmentManager());
            binding.scrollView.setAdapter(relationshipLiveAdapter);
        } else {
            liveAdapter = new SearchTeiLiveAdapter(presenter, getSupportFragmentManager());
            binding.scrollView.setAdapter(liveAdapter);
        }

        binding.formRecycler.setAdapter(new FormAdapter(getSupportFragmentManager(), this, presenter));
        binding.enrollmentButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.requestFocus();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                hideKeyboard();
                v.clearFocus();
                v.performClick();
            }
            return true;
        });

        binding.navigationBar.setOnNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.navigation_list_view:
                    showMap(false);
                    break;
                case R.id.navigation_map_view:
                    if (backDropActive) {
                        closeFilters();
                    }
                    showMap(true);
                    break;
            }
            return true;
        });
        try {
            binding.filterRecyclerLayout.setAdapter(filtersAdapter);
        } catch (Exception e) {
            Timber.e(e);
        }

        binding.mapLayerButton.setOnClickListener(view -> {
            new MapLayerDialog(teiMapManager)
                    .show(getSupportFragmentManager(), MapLayerDialog.class.getName());
        });

        CarouselAdapter carouselAdapter = new CarouselAdapter.Builder()
                .addOnTeiClickListener(
                        (teiUid, enrollmentUid, isDeleted) -> {
                            if (binding.mapCarousel.getCarouselEnabled()) {
                                if (fromRelationship) {
                                    presenter.addRelationship(teiUid, null, NetworkUtils.isOnline(this));
                                } else {
                                    updateTei = teiUid;
                                    presenter.onTEIClick(teiUid, enrollmentUid, isDeleted);
                                }
                            }
                            return true;
                        })
                .addOnSyncClickListener(
                        teiUid -> {
                            if (binding.mapCarousel.getCarouselEnabled()) {
                                presenter.onSyncIconClick(teiUid);
                            }
                            return true;
                        })
                .addOnDeleteRelationshipListener(relationshipUid -> {
                    if (binding.mapCarousel.getCarouselEnabled()) {
                        presenter.deleteRelationship(relationshipUid);
                    }
                    return true;
                })
                .addOnRelationshipClickListener(teiUid -> {
                    if (binding.mapCarousel.getCarouselEnabled()) {
                        presenter.onTEIClick(teiUid, null, false);
                    }
                    return true;
                })
                .addOnEventClickListener((teiUid, enrollmentUid, eventUid) -> {
                    if (binding.mapCarousel.getCarouselEnabled()) {
                        updateTei = teiUid;
                        updateEvent = eventUid;
                        presenter.onTEIClick(teiUid, enrollmentUid, false);
                    }
                    return true;
                })
                .addOnProfileImageClickListener(
                        path -> {
                            if (binding.mapCarousel.getCarouselEnabled()) {
                                new ImageDetailBottomDialog(
                                        null,
                                        new File(path)
                                ).show(
                                        getSupportFragmentManager(),
                                        ImageDetailBottomDialog.TAG
                                );
                            }
                            return Unit.INSTANCE;
                        }
                )
                .addOnNavigateClickListener(
                        uuid -> {
                            navigateToMap(teiMapManager.findFeature(uuid));
                            return Unit.INSTANCE;
                        }
                )
                .addProgram(presenter.getProgram())
                .build();
        binding.mapCarousel.setAdapter(carouselAdapter);

        binding.executePendingBindings();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setFabVisibility(false, false);
        }

        if (savedInstanceState != null) {
            presenter.restoreQueryData((HashMap<String, String>) savedInstanceState.getSerializable(Constants.QUERY_DATA));
        }
        updateFiltersSearch(presenter.getQueryData().size());
        teiMapManager = new TeiMapManager(binding.mapView);
        teiMapManager.setTeiFeatureType(presenter.getTrackedEntityType(tEType).featureType());
        teiMapManager.setEnrollmentFeatureType(presenter.getProgram() != null ? presenter.getProgram().featureType() : null);
        teiMapManager.setCarouselAdapter(carouselAdapter);
        teiMapManager.setOnMapClickListener(this);

        binding.mapCarousel.attachToMapManager(teiMapManager, (feature, found) -> {

            if (found && feature != null && FeatureExtensionsKt.isPoint(feature)) {
                binding.mapCarousel.showNavigateTo();
            } else {
                binding.mapCarousel.hideNavigateTo();
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FilterManager.getInstance().clearUnsupportedFilters();

        if (isMapVisible()) {
            animations.initMapLoading(binding.mapCarousel);
            binding.toolbarProgress.show();
            binding.progressLayout.setVisibility(View.GONE);
            if (updateTei != null) {
                if (updateEvent != null) {
                    ((CarouselAdapter) binding.mapCarousel.getAdapter()).updateItem(presenter.getEventInfo(updateEvent, updateTei));
                } else {
                    ((CarouselAdapter) binding.mapCarousel.getAdapter()).updateItem(presenter.getTeiInfo(updateTei));
                }
                updateEvent = null;
                updateTei = null;
            }
            animations.endMapLoading(binding.mapCarousel);
            binding.toolbarProgress.hide();
        }

        if (initSearchNeeded) {
            presenter.init(tEType);
        } else {
            initSearchNeeded = true;
        }

        teiMapManager.onResume();

        binding.setTotalFilters(FilterManager.getInstance().getTotalFilters());
    }

    @Override
    protected void onPause() {
        if (initSearchNeeded) {
            presenter.onDestroy();
        }
        teiMapManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        teiMapManager.onDestroy();
        presenter.onDestroy();

        FilterManager.getInstance().clearEnrollmentStatus();
        FilterManager.getInstance().clearEventStatus();
        FilterManager.getInstance().clearEnrollmentDate();
        FilterManager.getInstance().clearWorkingList();
        FilterManager.getInstance().clearSorting();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!ExtensionsKt.isKeyboardOpened(this)) {
            super.onBackPressed();
        } else {
            hideKeyboard();
        }
    }

    @Override
    public void onBackClicked() {
        hideKeyboard();
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.mapView.onSaveInstanceState(outState);
        outState.putSerializable(Constants.QUERY_DATA, presenter.getQueryData());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case FilterManager.OU_TREE:
                if (resultCode == Activity.RESULT_OK) {
                    filtersAdapter.notifyDataSetChanged();
                    updateFilters(FilterManager.getInstance().getTotalFilters());
                }
                break;
            case Constants.RQ_QR_SCANNER:
                if (resultCode == RESULT_OK) {
                    scanTextView.updateScanResult(data.getStringExtra(Constants.EXTRA_DATA));
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (isMapVisible() && teiMapManager.getPermissionsManager() != null) {
            teiMapManager.getPermissionsManager().onRequestPermissionsResult(requestCode, permissions, grantResults);
        } else if (requestCode == ACCESS_LOCATION_PERMISSION_REQUEST) {
            initSearchNeeded = false;
        }

    }

    @Override
    public void onMapPositionClick(CoordinatesView coordinatesView) {
        initSearchNeeded = false;
        super.onMapPositionClick(coordinatesView);
    }

    @Override
    public void onsScanClicked(Intent intent, @NotNull ScanTextView scanTextView) {
        initSearchNeeded = false;
        super.onsScanClicked(intent, scanTextView);
    }

    @Override
    public void updateFilters(int totalFilters) {
        binding.setTotalFilters(totalFilters);
        binding.executePendingBindings();
    }

    @Override
    public void updateFiltersSearch(int totalFilters) {
        binding.setTotalFiltersSearch(totalFilters);
        binding.executePendingBindings();
    }

    @Override
    public void showMoreOptions(View view) {
        PopupMenu popupMenu = new PopupMenu(this, view, Gravity.BOTTOM);
        try {
            Field[] fields = popupMenu.getClass().getDeclaredFields();
            for (Field field : fields) {
                if ("mPopup".equals(field.getName())) {
                    field.setAccessible(true);
                    Object menuPopupHelper = field.get(popupMenu);
                    Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                    Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuPopupHelper, true);
                    break;
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }
        popupMenu.getMenuInflater().inflate(R.menu.search_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.showHelp) {
                analyticsHelper().setEvent(SHOW_HELP, CLICK, SHOW_HELP);
                showTutorial(false);
            }
            return false;
        });

        boolean progressIsVisible = binding.progressLayout.getVisibility() == View.VISIBLE;

        if (!progressIsVisible)
            popupMenu.show();
    }

    //endregion

    //-----------------------------------------------------------------------
    //region SearchForm

    private void showMap(boolean showMap) {
        if (binding.messageContainer.getVisibility() == View.GONE) {
            binding.scrollView.setVisibility(showMap ? View.GONE : View.VISIBLE);
            binding.mapView.setVisibility(showMap ? View.VISIBLE : View.GONE);
            binding.mapCarousel.setVisibility(showMap ? View.VISIBLE : View.GONE);

            if (showMap) {
                binding.toolbarProgress.setVisibility(View.VISIBLE);
                binding.toolbarProgress.show();
                teiMapManager.init(() -> {
                    presenter.getMapData();
                    return Unit.INSTANCE;
                }, (permissionManager) -> {
                    permissionManager.requestLocationPermissions(this);
                    return Unit.INSTANCE;
                });
            } else {
                binding.mapLayerButton.setVisibility(View.GONE);
            }

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                setFabVisibility(!needsSearch.get() && !showMap, true);
            }
        }
    }

    @Override
    public void setForm(List<TrackedEntityAttribute> trackedEntityAttributes, @Nullable Program program, HashMap<String, String> queryData,
                        List<ValueTypeDeviceRendering> renderingTypes) {
        //Form has been set.
        FormAdapter formAdapter = (FormAdapter) binding.formRecycler.getAdapter();
        formAdapter.setList(trackedEntityAttributes, program, queryData, renderingTypes);
        updateFiltersSearch(queryData.size());
    }

    @NonNull
    public Flowable<RowAction> rowActionss() {
        return ((FormAdapter) binding.formRecycler.getAdapter()).asFlowableRA();
    }

    @Override
    public void clearData() {
        if (!isMapVisible()) {
            binding.progressLayout.setVisibility(View.VISIBLE);
        }
        binding.scrollView.setVisibility(View.GONE);
    }

    @Override
    public void showFilterProgress() {
        runOnUiThread(() -> {
            if (isMapVisible()) {
                binding.toolbarProgress.setVisibility(View.VISIBLE);
                binding.toolbarProgress.show();
            } else {
                binding.progressLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void setTutorial() {
        new Handler().postDelayed(() ->
                        HelpManager.getInstance().show(getActivity(),
                                HelpManager.TutorialName.TEI_SEARCH,
                                null),
                500);
    }

    //endregion

    //---------------------------------------------------------------------
    //region TEI LIST

    @Override
    public void setLiveData(LiveData<PagedList<SearchTeiModel>> liveData) {
        if (!fromRelationship) {
            liveData.observe(this, searchTeiModels -> {
                org.dhis2.data.tuples.Pair<String, Boolean> data = presenter.getMessage(searchTeiModels);
                presenter.checkFilters(data.val0().isEmpty());
                if (data.val0().isEmpty()) {
                    binding.messageContainer.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    liveAdapter.submitList(searchTeiModels);
                    binding.progressLayout.setVisibility(View.GONE);
                    CountingIdlingResourceSingleton.INSTANCE.decrement();
                } else {
                    binding.progressLayout.setVisibility(View.GONE);
                    binding.messageContainer.setVisibility(View.VISIBLE);
                    binding.message.setText(data.val0());
                    binding.scrollView.setVisibility(View.GONE);
                    CountingIdlingResourceSingleton.INSTANCE.decrement();
                }
                if (!searchTeiModels.isEmpty() && !data.val1()) {
                    showHideFilter();
                }
            });
        } else {
            liveData.observeForever(searchTeiModels -> {
                org.dhis2.data.tuples.Pair<String, Boolean> data = presenter.getMessage(searchTeiModels);
                if (data.val0().isEmpty()) {
                    binding.messageContainer.setVisibility(View.GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    relationshipLiveAdapter.submitList(searchTeiModels);
                    binding.progressLayout.setVisibility(View.GONE);
                } else {
                    binding.progressLayout.setVisibility(View.GONE);
                    binding.messageContainer.setVisibility(View.VISIBLE);
                    binding.message.setText(data.val0());
                    binding.scrollView.setVisibility(View.GONE);
                }
                CountingIdlingResourceSingleton.INSTANCE.decrement();
                if (!presenter.getQueryData().isEmpty() && data.val1())
                    setFabIcon(false);
            });
        }
        updateFilters(FilterManager.getInstance().getTotalFilters());
    }

    @Override
    public void setFiltersVisibility(boolean showFilters) {
        binding.filterCounter.setVisibility(showFilters ? View.VISIBLE : View.GONE);
        binding.searchFilterGeneral.setVisibility(showFilters ? View.VISIBLE : View.GONE);
    }

    @Override
    public void clearList(String uid) {
        this.initialProgram = uid;
        if (uid == null)
            binding.programSpinner.setSelection(0);
    }
    //endregion

    @Override
    public void setPrograms(List<Program> programs) {
        binding.programSpinner.setAdapter(new ProgramAdapter(this, R.layout.spinner_program_layout, R.id.spinner_text, programs, presenter.getTrackedEntityName().displayName()));
        if (initialProgram != null && !initialProgram.isEmpty())
            setInitialProgram(programs);
        else
            binding.programSpinner.setSelection(0);
        try {
            Field popup = Spinner.class.getDeclaredField("mPopup");
            popup.setAccessible(true);

            // Get private mPopup member variable and try cast to ListPopupWindow
            android.widget.ListPopupWindow popupWindow = (android.widget.ListPopupWindow) popup.get(binding.programSpinner);

            // Set popupWindow height to 500px
            popupWindow.setHeight(500);
        } catch (NoClassDefFoundError | ClassCastException | NoSuchFieldException | IllegalAccessException e) {
            // silently fail...
        }
        binding.programSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                if (pos > 0) {
                    analyticsHelper().setEvent(CHANGE_PROGRAM, CLICK, CHANGE_PROGRAM);
                    Program selectedProgram = (Program) adapterView.getItemAtPosition(pos - 1);
                    setProgramColor(presenter.getProgramColor(selectedProgram.uid()));
                    presenter.setProgram((Program) adapterView.getItemAtPosition(pos - 1));
                } else if (programs.size() == 1 && pos != 0) {
                    presenter.setProgram(programs.get(0));
                } else {
                    presenter.setProgram(null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        teiMapManager.setMapStyle(
                new MapStyle(
                        presenter.getTEIColor(),
                        presenter.getSymbolIcon(),
                        presenter.getEnrollmentColor(),
                        presenter.getEnrollmentSymbolIcon(),
                        presenter.getProgramStageStyle(),
                        ColorUtils.getPrimaryColor(this, ColorUtils.ColorType.PRIMARY_DARK)
                ));
    }

    private void updateMapVisibility(Program newProgram) {
        String currentProgram = presenter.getProgram() != null ? presenter.getProgram().uid() : null;
        String selectedProgram = newProgram != null ? newProgram.uid() : null;
        boolean programChanged = !Objects.equals(currentProgram, selectedProgram);
        if (isMapVisible() && programChanged) {
            showMap(false);
        }
    }

    private void setInitialProgram(List<Program> programs) {
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).uid().equals(initialProgram)) {
                binding.programSpinner.setSelection(i + 1);
            }
        }
    }

    @Override
    public void setProgramColor(String color) {
        int programTheme = ColorUtils.getThemeFromColor(color);
        int programColor = ColorUtils.getColorFrom(color, ColorUtils.getPrimaryColor(getContext(), ColorUtils.ColorType.PRIMARY));


        SharedPreferences prefs = getAbstracContext().getSharedPreferences(
                Constants.SHARE_PREFS, Context.MODE_PRIVATE);
        if (programTheme != -1) {
            prefs.edit().putInt(Constants.PROGRAM_THEME, programTheme).apply();
            binding.enrollmentButton.setSupportImageTintList(ColorStateList.valueOf(programColor));
            binding.mainToolbar.setBackgroundColor(programColor);
            binding.backdropLayout.setBackgroundColor(programColor);
            binding.navigationBar.setIconsColor(programColor);
        } else {
            prefs.edit().remove(Constants.PROGRAM_THEME).apply();
            int colorPrimary;
            switch (prefs.getInt(Constants.THEME, R.style.AppTheme)) {
                case R.style.RedTheme:
                    colorPrimary = R.color.colorPrimaryRed;
                    break;
                case R.style.OrangeTheme:
                    colorPrimary = R.color.colorPrimaryOrange;
                    break;
                case R.style.GreenTheme:
                    colorPrimary = R.color.colorPrimaryGreen;
                    break;
                default:
                    colorPrimary = R.color.colorPrimary;
                    break;
            }
            binding.enrollmentButton.setSupportImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colorPrimary)));
            binding.mainToolbar.setBackgroundColor(ContextCompat.getColor(this, colorPrimary));
            binding.backdropLayout.setBackgroundColor(ContextCompat.getColor(this, colorPrimary));
            binding.navigationBar.setIconsColor(ContextCompat.getColor(this, colorPrimary));
        }

        setTheme(prefs.getInt(Constants.PROGRAM_THEME, prefs.getInt(Constants.THEME, R.style.AppTheme)));
        binding.executePendingBindings();
        binding.clearFilter.setImageDrawable(
                ColorUtils.tintDrawableWithColor(
                        binding.clearFilter.getDrawable(),
                        ColorUtils.getPrimaryColor(this, ColorUtils.ColorType.PRIMARY)
                ));
        binding.closeFilter.setImageDrawable(
                ColorUtils.tintDrawableWithColor(
                        binding.closeFilter.getDrawable(),
                        ColorUtils.getPrimaryColor(this, ColorUtils.ColorType.PRIMARY)
                ));
        binding.progress.setIndeterminateDrawable(
                ColorUtils.tintDrawableWithColor(
                        binding.progress.getIndeterminateDrawable(),
                        ColorUtils.getPrimaryColor(this, ColorUtils.ColorType.PRIMARY)
                ));

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            TypedValue typedValue = new TypedValue();
            TypedArray a = obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorPrimaryDark});
            int colorToReturn = a.getColor(0, 0);
            a.recycle();
            window.setStatusBarColor(colorToReturn);
        }
    }

    @Override
    public String fromRelationshipTEI() {
        return fromRelationshipTeiUid;
    }

    @Override
    public void setFabIcon(boolean needsSearch) {
        this.needsSearch.set(needsSearch);
        animSearchFab(needsSearch);
    }

    private void animSearchFab(boolean hasQuery) {
        if (hasQuery) {
            PropertyValuesHolder scalex = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f);
            PropertyValuesHolder scaley = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f);
            animation = ObjectAnimator.ofPropertyValuesHolder(binding.enrollmentButton, scalex, scaley);
            animation.setRepeatCount(ValueAnimator.INFINITE);
            animation.setRepeatMode(ValueAnimator.REVERSE);
            animation.setDuration(500);
            animation.start();
        } else {
            if (animation != null) {
                animation.cancel();
            }
            hideKeyboard();
        }
    }

    @Override
    public void showHideFilter() {
        binding.filterRecyclerLayout.setVisibility(View.GONE);
        binding.formRecycler.setVisibility(View.VISIBLE);

        swipeFilters(false);
    }

    @Override
    public void showHideFilterGeneral() {
        binding.filterRecyclerLayout.setVisibility(View.VISIBLE);
        binding.formRecycler.setVisibility(View.GONE);

        swipeFilters(true);
    }

    private void swipeFilters(boolean general) {
        Transition transition = new ChangeBounds();
        transition.setDuration(200);
        TransitionManager.beginDelayedTransition(binding.backdropLayout, transition);
        if (backDropActive && !general && switchOpenClose == 0)
            switchOpenClose = 1;
        else if (backDropActive && general && switchOpenClose == 1)
            switchOpenClose = 0;
        else {
            switchOpenClose = general ? 0 : 1;
            backDropActive = !backDropActive;
        }
        binding.filterOpen.setVisibility(backDropActive ? View.VISIBLE : View.GONE);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
            activeFilter(general);
    }

    private void activeFilter(boolean general) {
        ConstraintSet initSet = new ConstraintSet();
        initSet.clone(binding.backdropLayout);

        if (backDropActive) {
            initSet.connect(R.id.mainLayout, ConstraintSet.TOP, general ? R.id.filterRecyclerLayout : R.id.form_recycler, ConstraintSet.BOTTOM, general ? 50 : 0);
        } else {
            initSet.connect(R.id.mainLayout, ConstraintSet.TOP, R.id.backdropGuideTop, ConstraintSet.BOTTOM, 0);
        }

        setFabVisibility(
                backDropActive && !general || (!needsSearch.get() && !isMapVisible()),
                !backDropActive || general
        );
        setCarouselVisibility(backDropActive);

        initSet.applyTo(binding.backdropLayout);
    }

    @Override
    public void setFilters(List<FilterItem> filtersToDisplay) {
        filtersAdapter.submitList(filtersToDisplay);
    }

    private void setFabVisibility(boolean show, boolean onNavBar) {
        binding.enrollmentButton.animate()
                .setDuration(500)
                .translationX(show ? 0 : 500)
                .translationY(onNavBar ? -ExtensionsKt.getDp(56) : 0)
                .start();
    }

    private void setCarouselVisibility(boolean backDropActive) {
        binding.mapCarousel.animate()
                .setDuration(500)
                .translationY(backDropActive ? 600 : 0)
                .start();
    }

    @Override
    public void closeFilters() {
        if (switchOpenClose == 0)
            showHideFilterGeneral();
        else
            showHideFilter();
    }

    @Override
    public void clearFilters() {
        if (switchOpenClose == 0) {
            filtersAdapter.notifyDataSetChanged();
            FilterManager.getInstance().clearAllFilters();
        } else
            presenter.onClearClick();
    }

    @Override
    public void showTutorial(boolean shaked) {
        setTutorial();
    }

    @Override
    public void openOrgUnitTreeSelector() {
        Intent ouTreeIntent = new Intent(this, OUTreeActivity.class);
        Bundle bundle = OUTreeActivity.Companion.getBundle(initialProgram);
        ouTreeIntent.putExtras(bundle);
        startActivityForResult(ouTreeIntent, FilterManager.OU_TREE);
    }

    @Override
    public void showPeriodRequest(Pair<FilterManager.PeriodRequest, Filters> periodRequest) {
        if (periodRequest.getFirst() == FilterManager.PeriodRequest.FROM_TO) {
            DateUtils.getInstance().showFromToSelector(this, datePeriod -> {
                if (periodRequest.getSecond() == Filters.PERIOD) {
                    FilterManager.getInstance().addPeriod(datePeriod);
                } else {
                    FilterManager.getInstance().addEnrollmentPeriod(datePeriod);
                }
            });
        } else {
            DateUtils.getInstance().showPeriodDialog(this, datePeriods -> {
                        if (periodRequest.getSecond() == Filters.PERIOD) {
                            FilterManager.getInstance().addPeriod(datePeriods);
                        } else {
                            FilterManager.getInstance().addEnrollmentPeriod(datePeriods);
                        }
                    },
                    true);
        }
    }

    @Override
    public void openDashboard(String teiUid, String programUid, String enrollmentUid) {
        startActivity(TeiDashboardMobileActivity.intent(this, teiUid, enrollmentUid != null ? programUid : null, enrollmentUid));
    }

    @Override
    public void couldNotDownload(String typeName) {
        displayMessage(getString(R.string.download_tei_error, typeName));
    }

    @Override
    public void goToEnrollment(String enrollmentUid, String programUid) {
        Intent intent = EnrollmentActivity.Companion.getIntent(this,
                enrollmentUid,
                programUid,
                EnrollmentActivity.EnrollmentMode.NEW,
                fromRelationshipTEI() != null);
        startActivity(intent);
    }

    /*region MAP*/
    @Override
    public void setMap(TrackerMapData trackerMapData) {
        binding.progressLayout.setVisibility(View.GONE);

        org.dhis2.data.tuples.Pair<String, Boolean> data = presenter.getMessage(trackerMapData.getTeiModels());
        if (data.val0().isEmpty()) {
            binding.messageContainer.setVisibility(View.GONE);
            binding.mapView.setVisibility(View.VISIBLE);

            List<CarouselItemModel> allItems = new ArrayList<>();
            allItems.addAll(trackerMapData.getTeiModels());
            allItems.addAll(trackerMapData.getEventModels());
            for (SearchTeiModel searchTeiModel : trackerMapData.getTeiModels()) {
                allItems.addAll(new MapRelationshipToRelationshipMapModel().mapList(searchTeiModel.getRelationships()));
            }

            teiMapManager.update(
                    trackerMapData.getTeiFeatures(),
                    trackerMapData.getEventFeatures(),
                    trackerMapData.getDataElementFeaturess(),
                    trackerMapData.getTeiBoundingBox()
            );
            updateCarousel(allItems);
            binding.mapLayerButton.setVisibility(View.VISIBLE);


        } else {
            binding.messageContainer.setVisibility(View.VISIBLE);
            binding.message.setText(data.val0());
            binding.mapView.setVisibility(View.GONE);
        }
        if (!trackerMapData.getTeiModels().isEmpty() && !data.val1()) {
            showHideFilter();
        }
        animations.endMapLoading(binding.mapCarousel);
        binding.toolbarProgress.hide();
        updateFilters(FilterManager.getInstance().getTotalFilters());
    }

    private void updateCarousel(List<CarouselItemModel> allItems) {
        if (binding.mapCarousel.getAdapter() != null) {
            ((CarouselAdapter) binding.mapCarousel.getAdapter()).updateAllData(allItems, teiMapManager.mapLayerManager);
        }
    }


    @Override
    public Consumer<D2Progress> downloadProgress() {
        return progress -> Snackbar.make(binding.getRoot(), getString(R.string.downloading), Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public boolean isMapVisible() {
        return binding.mapView.getVisibility() == View.VISIBLE ||
                binding.navigationBar.getSelectedItemId() == R.id.navigation_map_view;
    }


    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        Feature featureFound = teiMapManager.markFeatureAsSelected(point, null);
        if (featureFound != null) {
            binding.mapCarousel.scrollToFeature(featureFound);
            return true;
        }
        return false;
    }

    private void navigateToMap(Feature feature) {
        LatLng point = FeatureExtensionsKt.getPointLatLng(feature);
        String longitude = String.valueOf(point.getLongitude());
        String latitude = String.valueOf(point.getLatitude());
        String location = "geo:0,0?q=" + latitude + "," + longitude + "";

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(location));
        startActivity(intent);
    }

    /*endregion*/
}
