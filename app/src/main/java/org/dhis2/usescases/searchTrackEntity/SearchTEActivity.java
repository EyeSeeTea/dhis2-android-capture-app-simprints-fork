package org.dhis2.usescases.searchTrackEntity;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableBoolean;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import com.google.android.material.snackbar.Snackbar;
import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import org.dhis2.App;
import org.dhis2.Bindings.ExtensionsKt;
import org.dhis2.Bindings.ViewExtensionsKt;
import org.dhis2.R;
import org.dhis2.animations.CarouselViewAnimations;
import org.dhis2.commons.dialogs.DialogClickListener;
import org.dhis2.data.biometrics.BiometricsClientFactory;
import org.dhis2.data.biometrics.IdentifyResult;
import org.dhis2.data.biometrics.RegisterResult;
import org.dhis2.data.forms.dataentry.FormView;
import org.dhis2.data.forms.dataentry.ProgramAdapter;
import org.dhis2.data.forms.dataentry.fields.FieldViewModelFactory;
import org.dhis2.data.location.LocationProvider;
import org.dhis2.databinding.ActivitySearchBinding;
import org.dhis2.form.data.FormRepository;
import org.dhis2.form.model.DispatcherProvider;
import org.dhis2.form.model.FieldUiModel;
import org.dhis2.uicomponents.map.ExternalMapNavigation;
import org.dhis2.uicomponents.map.carousel.CarouselAdapter;
import org.dhis2.uicomponents.map.layer.MapLayerDialog;
import org.dhis2.uicomponents.map.managers.TeiMapManager;
import org.dhis2.uicomponents.map.mapper.MapRelationshipToRelationshipMapModel;
import org.dhis2.uicomponents.map.model.CarouselItemModel;
import org.dhis2.uicomponents.map.model.MapStyle;
import org.dhis2.usescases.enrollment.EnrollmentActivity;
import org.dhis2.usescases.general.ActivityGlobalAbstract;
import org.dhis2.commons.orgunitselector.OUTreeFragment;
import org.dhis2.commons.orgunitselector.OnOrgUnitSelectionFinished;
import org.dhis2.usescases.searchTrackEntity.adapters.SearchTeiLiveAdapter;
import org.dhis2.usescases.searchTrackEntity.adapters.SearchTeiModel;
import org.dhis2.usescases.teiDashboard.TeiDashboardMobileActivity;
import org.dhis2.commons.resources.ColorUtils;
import org.dhis2.utils.Constants;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.HelpManager;
import org.dhis2.utils.LastSelection;
import org.dhis2.utils.NetworkUtils;
import org.dhis2.utils.OrientationUtilsKt;
import org.dhis2.utils.customviews.BreakTheGlassBottomDialog;
import org.dhis2.commons.dialogs.CustomDialog;
import org.dhis2.utils.customviews.ImageDetailBottomDialog;
import org.dhis2.utils.customviews.navigationbar.NavigationPageConfigurator;
import org.dhis2.commons.filters.FilterItem;
import org.dhis2.commons.filters.FilterManager;
import org.dhis2.commons.filters.Filters;
import org.dhis2.commons.filters.FiltersAdapter;
import org.dhis2.commons.idlingresource.CountingIdlingResourceSingleton;
import org.hisp.dhis.android.core.arch.call.D2Progress;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.program.Program;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dhis2.org.analytics.charts.ui.GroupAnalyticsFragment;
import io.reactivex.functions.Consumer;
import kotlin.Pair;
import kotlin.Unit;
import timber.log.Timber;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_CONFIRM_IDENTITY_REQUEST;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_ENABLED;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_ENROLL_LAST_REQUEST;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_IDENTIFY_REQUEST;
import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_USER_NOT_FOUND;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconBasic;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconFunnel;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconNoneOfTheAbove;
import static org.dhis2.usescases.biometrics.ExtensionsKt.getBioIconSearch;
import static org.dhis2.usescases.biometrics.ExtensionsKt.isBiometricText;
import static org.dhis2.usescases.eventsWithoutRegistration.eventInitial.EventInitialPresenter.ACCESS_LOCATION_PERMISSION_REQUEST;
import static org.dhis2.utils.analytics.AnalyticsConstants.CHANGE_PROGRAM;
import static org.dhis2.utils.analytics.AnalyticsConstants.CLICK;

public class SearchTEActivity extends ActivityGlobalAbstract implements SearchTEContractsModule.View,
        MapboxMap.OnMapClickListener, OnOrgUnitSelectionFinished {

    ActivitySearchBinding binding;
    @Inject
    SearchTEContractsModule.Presenter presenter;
    @Inject
    CarouselViewAnimations animations;
    @Inject
    FiltersAdapter filtersAdapter;
    @Inject
    ExternalMapNavigation mapNavigation;
    @Inject
    FieldViewModelFactory fieldViewModelFactory;
    @Inject
    FormRepository formRepository;
    @Inject
    LocationProvider locationProvider;
    @Inject
    DispatcherProvider dispatchers;
    @Inject
    NavigationPageConfigurator pageConfigurator;

    private String initialProgram;
    private String tEType;

    private boolean fromRelationship = false;
    private String fromRelationshipTeiUid;
    private boolean backDropActive;
    private boolean fromAnalytics = false;
    /**
     * 0 - it is general filter
     * 1 - it is search filter
     * 2 - it was closed
     */
    private int switchOpenClose = 2;

    ObservableBoolean needsSearch = new ObservableBoolean(true);
    ObservableBoolean showClear = new ObservableBoolean(false);

    private SearchTeiLiveAdapter liveAdapter;
    private TeiMapManager teiMapManager;
    public boolean initSearchNeeded = true;
    private ObjectAnimator animation = null;
    private String updateTei;
    private String updateEvent;
    private CarouselAdapter carouselAdapter;
    private FormView formView;

    private CustomDialog biometricsErrorDialog;

    private LastSelection lastSelection;

    //---------------------------------------------------------------------------------------------

    //region LIFECYCLE
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        tEType = getIntent().getStringExtra("TRACKED_ENTITY_UID");
        initialProgram = getIntent().getStringExtra("PROGRAM_UID");

        ((App) getApplicationContext()).userComponent().plus(new SearchTEModule(this, tEType, initialProgram, getContext())).inject(this);

        formView = new FormView.Builder()
                .repository(formRepository)
                .locationProvider(locationProvider)
                .dispatcher(dispatchers)
                .onItemChangeListener(action -> {
                    fieldViewModelFactory.fieldProcessor().onNext(action);
                    return Unit.INSTANCE;
                })
                .activityForResultListener(() -> {
                    initSearchNeeded = false;
                    return Unit.INSTANCE;
                })
                .needToForceUpdate(true)
                .factory(getSupportFragmentManager())
                .build();

        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_search);
        binding.setPresenter(presenter);
        binding.setNeedsSearch(needsSearch);
        binding.setShowClear(showClear);
        binding.setTotalFilters(FilterManager.getInstance().getTotalFilters());
        binding.setTotalFiltersSearch(presenter.getQueryData().size());

        try {
            fromRelationship = getIntent().getBooleanExtra("FROM_RELATIONSHIP", false);
            fromRelationshipTeiUid = getIntent().getStringExtra("FROM_RELATIONSHIP_TEI");
        } catch (Exception e) {
            Timber.d(e.getMessage());
        }

        ViewExtensionsKt.clipWithRoundedCorners(binding.mainLayout, ExtensionsKt.getDp(16));
        ViewExtensionsKt.clipWithRoundedCorners(binding.mapView, ExtensionsKt.getDp(16));
        liveAdapter = new SearchTeiLiveAdapter(fromRelationship, presenter, getSupportFragmentManager());
        binding.scrollView.setAdapter(liveAdapter);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.formViewContainer, formView).commit();

        binding.enrollmentButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.requestFocus();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                hideKeyboard();
                v.clearFocus();
                presenter.onFabClick(needsSearch.get());
            }
            return true;
        });

        binding.navigationBar.pageConfiguration(pageConfigurator);
        binding.navigationBar.setOnNavigationItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.navigation_list_view:
                    binding.mainLayout.setVisibility(View.VISIBLE);
                    binding.mainComponent.setVisibility(GONE);
                    showMap(false);
                    showSearchAndFilterButtons();
                    break;
                case R.id.navigation_map_view:
                    if (backDropActive) {
                        closeFilters();
                    }
                    binding.mainLayout.setVisibility(View.VISIBLE);
                    binding.mainComponent.setVisibility(GONE);
                    showMap(true);
                    showSearchAndFilterButtons();
                    break;
                case R.id.navigation_analytics:
                    fromAnalytics = true;
                    if (backDropActive) {
                        closeFilters();
                    }
                    binding.mainComponent.setVisibility(View.VISIBLE);
                    binding.mainLayout.setVisibility(GONE);
                    showAnalytics();
                    hideSearchAndFilterButtons();
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

        binding.mapPositionButton.setOnClickListener(view -> {
            teiMapManager.centerCameraOnMyPosition((permissionManager) -> {
                permissionManager.requestLocationPermissions(this);
                return Unit.INSTANCE;
            });
        });

        binding.executePendingBindings();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            setFabVisibility(false, false);
        }

        if (savedInstanceState != null) {
            presenter.restoreQueryData((HashMap<String, String>) savedInstanceState.getSerializable(Constants.QUERY_DATA));
        }
        updateFiltersSearch(presenter.getQueryData().size());
        teiMapManager = new TeiMapManager(binding.mapView);
        getLifecycle().addObserver(teiMapManager);
        teiMapManager.onCreate(savedInstanceState);
        teiMapManager.setTeiFeatureType(presenter.getTrackedEntityType(tEType).featureType());
        teiMapManager.setEnrollmentFeatureType(presenter.getProgram() != null ? presenter.getProgram().featureType() : null);
        teiMapManager.setOnMapClickListener(this);

        binding.showListBtn.setOnClickListener(view -> {
            presenter.resetSearch();
            binding.messageContainer.setVisibility(GONE);
            binding.scrollView.setVisibility(View.VISIBLE);
            binding.progressLayout.setVisibility(GONE);
        });

        binding.biometricsButtonsContainer.identificationPlusButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(this, getBioIconBasic(getContext())));
        binding.biometricsButtonsContainer.noneOfTheAboveButtonIcon.setImageDrawable(
                AppCompatResources.getDrawable(this, getBioIconNoneOfTheAbove(getContext())));
        binding.biometricSearch.setImageDrawable(
                AppCompatResources.getDrawable(this, getBioIconSearch(getContext())));
        binding.biometricSearch.setOnClickListener(v -> {
            searchByBiometrics();
        });
    }

    private void searchByBiometrics() {
        BiometricsClientFactory.INSTANCE.get(this).identify(this);
    }


    @Override
    public void sendBiometricsConfirmIdentity(String sessionId, String guid, String teiUid,
            String enrollmentUid, boolean isOnline) {
        lastSelection = new LastSelection(teiUid, enrollmentUid, isOnline);
        BiometricsClientFactory.INSTANCE.get(this).confirmIdentify(this, sessionId, guid);
    }

    @Override
    public void sendBiometricsNoneSelected(String sessionId) {
        BiometricsClientFactory.INSTANCE.get(this).noneSelected(this, sessionId);
    }

    @Override
    public void biometricsEnrollmentLast(String sessionId) {
        BiometricsClientFactory.INSTANCE.get(this).registerLast(this, sessionId);
    }


    @Override
    public void showNoneOfTheAboveButton() {
        binding.biometricsButtonsContainer.noneOfTheAboveButton.setVisibility(VISIBLE);
    }

    @Override
    public void hideNoneOfTheAboveButton() {
        binding.biometricsButtonsContainer.noneOfTheAboveButton.setVisibility(GONE);
    }

    @Override
    public void showIdentificationPlusButton() {
        binding.biometricsButtonsContainer.identificationPlusButton.setVisibility(VISIBLE);
    }

    @Override
    public void hideIdentificationPlusButton() {
        binding.biometricsButtonsContainer.identificationPlusButton.setVisibility(GONE);
    }

    @Override
    public void activeBiometricsSearch(boolean active) {
        if (active) {
            binding.biometricSearch.setImageDrawable(AppCompatResources.getDrawable(this,
                    getBioIconFunnel(this)));
        } else {
            binding.biometricSearch.setImageDrawable(AppCompatResources.getDrawable(this,
                    getBioIconSearch(this)));
        }
    }

    @Override
    public void setBiometricsVisibility(boolean visible) {
        if (visible){
            binding.biometricSearch.setVisibility(VISIBLE);
        } else {
            binding.biometricSearch.setVisibility(GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FilterManager.getInstance().clearUnsupportedFilters();

        binding.navigationBar.onResume();

        if (initSearchNeeded) {
            presenter.init(tEType);
        } else {
            initSearchNeeded = true;
        }

        binding.setTotalFilters(FilterManager.getInstance().getTotalFilters());

    }

    @Override
    protected void onPause() {
        presenter.setOpeningFilterToNone();
        if (initSearchNeeded) {
            presenter.onDestroy();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        teiMapManager.onDestroy();
        presenter.onDestroy();

        FilterManager.getInstance().clearEnrollmentStatus();
        FilterManager.getInstance().clearEventStatus();
        FilterManager.getInstance().clearEnrollmentDate();
        FilterManager.getInstance().clearWorkingList(false);
        FilterManager.getInstance().clearSorting();
        FilterManager.getInstance().clearAssignToMe();
        FilterManager.getInstance().clearFollowUp();

        presenter.clearOtherFiltersIfWebAppIsConfig();

        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        teiMapManager.onLowMemory();
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
        teiMapManager.onSaveInstanceState(outState);
        outState.putSerializable(Constants.QUERY_DATA, presenter.getQueryData());
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
    public void updateFilters(int totalFilters) {
        binding.setTotalFilters(totalFilters);
        binding.executePendingBindings();
    }

    @Override
    public void updateFiltersSearch(int totalFilters) {
        binding.setTotalFiltersSearch(totalFilters);
        binding.executePendingBindings();
    }

    //endregion


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case BIOMETRICS_IDENTIFY_REQUEST: {
                if (resultCode == RESULT_OK) {

                    IdentifyResult result = BiometricsClientFactory.INSTANCE.get(
                            this).handleIdentifyResponse(data);

                    if (result instanceof IdentifyResult.Completed) {
                        IdentifyResult.Completed completedResult =
                                (IdentifyResult.Completed) result;

                        presenter.searchOnBiometrics(completedResult.getGuids(),
                                completedResult.getSessionId());
                    } else if (result instanceof IdentifyResult.BiometricsDeclined) {
                        Toast.makeText(getContext(), R.string.biometrics_declined,
                                Toast.LENGTH_SHORT).show();

                    } else if (result instanceof IdentifyResult.UserNotFound) {
                        Toast.makeText(getContext(), R.string.biometrics_user_not_found,
                                Toast.LENGTH_SHORT).show();
                        presenter.searchOnBiometrics(
                                Collections.singletonList(BIOMETRICS_USER_NOT_FOUND),
                                ((IdentifyResult.UserNotFound) result).getSessionId());
                    } else if (result instanceof IdentifyResult.Failure) {
                        showBiometricsErrorDialog();
                    }
                } else {
                    Toast.makeText(getContext(), R.string.biometrics_declined,
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case BIOMETRICS_ENROLL_LAST_REQUEST: {
                if (resultCode == RESULT_OK) {
                    RegisterResult result =
                            BiometricsClientFactory.INSTANCE.get(this).handleRegisterResponse(data);

                    RegisterResult.Completed completed = (RegisterResult.Completed) result;

                    if (result instanceof RegisterResult.Completed) {
                        presenter.enrollmentWithBiometrics(completed.getGuid());
                    } else {
                        Toast.makeText(getContext(), R.string.biometrics_declined,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
            case BIOMETRICS_CONFIRM_IDENTITY_REQUEST: {
                if (lastSelection != null) {
                    presenter.onTEIClick(lastSelection.getTeiUid(), lastSelection.getEnrollmentUid(),
                            lastSelection.isOnline());
                    lastSelection = null;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showBiometricsErrorDialog() {
        String title = getString(R.string.biometrics_error_dialog_title);
        String desc = getString(R.string.biometrics_error_dialog_desc);
        String posButton = getString(R.string.biometrics_try_again);
        String negButton = getString(R.string.cancel);
        DialogClickListener dialogClickListener = new DialogClickListener() {
            @Override
            public void onPositive() {
                searchByBiometrics();
            }

            @Override
            public void onNegative() {
                if (null != biometricsErrorDialog) {
                    biometricsErrorDialog.dismiss();
                }
            }
        };
        biometricsErrorDialog = new CustomDialog(getContext(), title, desc, posButton, negButton, 0,
                dialogClickListener);
        biometricsErrorDialog.show();
    }

    //-----------------------------------------------------------------------
    //region SearchForm

    private void showAnalytics() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.mainComponent, GroupAnalyticsFragment.Companion.forProgram(initialProgram)).commit();
    }

    private void showMap(boolean showMap) {
        if (binding.messageContainer.getVisibility() == GONE) {
            binding.mainComponent.setVisibility(GONE);
            binding.scrollView.setVisibility(showMap ? GONE : View.VISIBLE);
            binding.mapView.setVisibility(showMap ? View.VISIBLE : GONE);
            binding.mapCarousel.setVisibility(showMap ? View.VISIBLE : GONE);

            if (showMap) {
                initializeCarousel();
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
                removeCarousel();
                binding.mapLayerButton.setVisibility(View.GONE);
                binding.mapPositionButton.setVisibility(View.GONE);
                presenter.getListData();
            }

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                setFabVisibility(shouldDisplayButton(), !binding.navigationBar.isHidden());
            }
        }
    }

    public boolean shouldDisplayButton() {
        return (backDropActive && switchOpenClose == 1) || (!needsSearch.get() && !isMapVisible());
    }

    private void removeCarousel() {
        carouselAdapter = null;
        teiMapManager.setCarouselAdapter(null);
        binding.mapCarousel.setAdapter(null);
    }

    private void initializeCarousel() {
        carouselAdapter = new CarouselAdapter.Builder()
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
                .addOnRelationshipClickListener((teiUid, ownerType) -> {
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
                            Feature feature = teiMapManager.findFeature(uuid);
                            if (feature != null) {
                                startActivity(mapNavigation.navigateToMapIntent(feature));
                            }
                            return Unit.INSTANCE;
                        }
                )
                .addProgram(presenter.getProgram())
                .addMapManager(teiMapManager)
                .build();
        teiMapManager.setCarouselAdapter(carouselAdapter);
        binding.mapCarousel.setAdapter(carouselAdapter);
        binding.mapCarousel.attachToMapManager(teiMapManager);
    }

    @Override
    public void setFormData(List<FieldUiModel> data) {
        data = removeBiometricsAttribute(data);

        formView.processItems(data);
        updateFiltersSearch(presenter.getQueryData().size());
    }

    private List<FieldUiModel> removeBiometricsAttribute(List<FieldUiModel> data) {
        List<FieldUiModel> finalData = new ArrayList<>();

        if (data == null) return finalData;

        for (int i = data.size() - 1; i >= 0; i--) {
            String label = data.get(i).getLabel();
            if (!isBiometricText(label)) {
                finalData.add(data.get(i));
            }
        }

        return finalData;
    }

    @Override
    public void clearData() {
        if (!isMapVisible()) {
            binding.progressLayout.setVisibility(View.VISIBLE);
        }
        binding.scrollView.setVisibility(GONE);
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

    private void hideSearchAndFilterButtons() {
        binding.searchFilter.setVisibility(GONE);
        binding.searchFilterGeneral.setVisibility(GONE);
        binding.filterCounterSearch.setVisibility(GONE);
        binding.filterCounter.setVisibility(GONE);
        if (OrientationUtilsKt.isLandscape()) {
            binding.enrollmentButton.setVisibility(GONE);
        }
    }

    private void showSearchAndFilterButtons() {
        if (fromAnalytics) {
            fromAnalytics = false;
            binding.searchFilter.setVisibility(View.VISIBLE);
            binding.searchFilterGeneral.setVisibility(View.VISIBLE);
            binding.filterCounterSearch.setVisibility(View.VISIBLE);
            binding.filterCounter.setVisibility(View.VISIBLE);
            if (OrientationUtilsKt.isLandscape()) {
                binding.enrollmentButton.setVisibility(View.VISIBLE);
            }
        }
    }

    //endregion

    //---------------------------------------------------------------------
    //region TEI LIST

    @Override
    public void setLiveData(LiveData<PagedList<SearchTeiModel>> liveData) {
        if (!fromRelationship) {
            liveData.observe(this, searchTeiModels -> {
                if (presenter.getBiometricsSearchStatus()) {
                    presenter.clearQueryData();

                    if (searchTeiModels.size() > 0) {
                        showNoneOfTheAboveButton();
                    } else {
                        hideNoneOfTheAboveButton();
                    }
                    showIdentificationPlusButton();

                    for (int i = 0; i < searchTeiModels.size(); i++) {
                        searchTeiModels.get(i).setBiometricsSearchStatus(true);
                    }
                } else {
                    for (int i = 0; i < searchTeiModels.size(); i++) {
                        searchTeiModels.get(i).setBiometricsSearchStatus(false);
                    }
                }

                SearchMessageResult data = presenter.getMessage(searchTeiModels);
                presenter.checkFilters(data.getMessage().isEmpty());
                if (data.getMessage().isEmpty()) {
                    binding.messageContainer.setVisibility(GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    liveAdapter.submitList(searchTeiModels);
                    binding.progressLayout.setVisibility(GONE);
                    CountingIdlingResourceSingleton.INSTANCE.decrement();
                } else {
                    binding.progressLayout.setVisibility(GONE);
                    binding.messageContainer.setVisibility(View.VISIBLE);
                    binding.message.setText(data.getMessage());
                    binding.scrollView.setVisibility(GONE);
                    CountingIdlingResourceSingleton.INSTANCE.decrement();
                }
                if (!searchTeiModels.isEmpty() && !data.getCanRegister() && data.getForceSearch() && !presenter.getBiometricsSearchStatus()) {
                    showHideFilter();
                    if (data.getShowButton()) {
                        binding.showListBtn.setVisibility(View.VISIBLE);
                    }
                }
            });
        } else {
            liveData.observeForever(searchTeiModels -> {
                SearchMessageResult data = presenter.getMessage(searchTeiModels);
                if (data.getMessage().isEmpty()) {
                    binding.messageContainer.setVisibility(GONE);
                    binding.scrollView.setVisibility(View.VISIBLE);
                    liveAdapter.submitList(searchTeiModels);
                    binding.progressLayout.setVisibility(GONE);
                } else {
                    binding.progressLayout.setVisibility(GONE);
                    binding.messageContainer.setVisibility(View.VISIBLE);
                    binding.message.setText(data.getMessage());
                    binding.scrollView.setVisibility(GONE);
                }
                CountingIdlingResourceSingleton.INSTANCE.decrement();
                if (!presenter.getQueryData().isEmpty() && data.getCanRegister())
                    setFabIcon(false);
            });
        }
        updateFilters(FilterManager.getInstance().getTotalFilters());
    }

    @Override
    public void setFiltersVisibility(boolean showFilters) {
        binding.filterCounter.setVisibility(showFilters ? View.VISIBLE : GONE);
        binding.searchFilterGeneral.setVisibility(showFilters ? View.VISIBLE : GONE);
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
                Program programSelected;
                if (pos == 0) {
                    programSelected = (Program) adapterView.getItemAtPosition(0);
                } else {
                    programSelected = (Program) adapterView.getItemAtPosition(pos - 1);
                }
                if (!programSelected.uid().equals(initialProgram)) {
                    liveAdapter.clearList();
                }
                if (pos > 0) {
                    analyticsHelper().setEvent(CHANGE_PROGRAM, CLICK, CHANGE_PROGRAM);
                    Program selectedProgram = (Program) adapterView.getItemAtPosition(pos - 1);
                    setProgramColor(presenter.getProgramColor(selectedProgram.uid()));
                    presenter.setProgram(selectedProgram);
                } else if (programs.size() == 1 && pos != 0) {
                    Program selectedProgram = programs.get(0);
                    presenter.setProgram(selectedProgram);
                } else {
                    presenter.setProgram(null);
                    binding.navigationBar.hide();
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

    @Override
    public void updateNavigationBar() {
        binding.navigationBar.pageConfiguration(pageConfigurator);
    }

    @Override
    public void displayMinNumberOfAttributesMessage(int minAttributes) {
        displayMessage(String.format(getString(R.string.search_min_num_attr), minAttributes));
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
            programColor = ContextCompat.getColor(this, colorPrimary);
        }
        binding.enrollmentButton.setSupportImageTintList(ColorStateList.valueOf(programColor));
        binding.clearFilterSearchButton.setSupportImageTintList(ColorStateList.valueOf(programColor));
        binding.mainToolbar.setBackgroundColor(programColor);
        binding.backdropLayout.setBackgroundColor(programColor);
        binding.navigationBar.setIconsColor(programColor);
        binding.totalFilterCount.setTextColor(programColor);
        binding.totalSearchCount.setTextColor(programColor);

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
        binding.filterRecyclerLayout.setVisibility(GONE);
        binding.formViewContainer.setVisibility(View.VISIBLE);

        swipeFilters(false);
    }

    @Override
    public void showHideFilterGeneral() {
        binding.filterRecyclerLayout.setVisibility(View.VISIBLE);
        binding.formViewContainer.setVisibility(GONE);

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
        ViewCompat.setElevation(binding.mainLayout, backDropActive ? 20 : 0);
        ViewCompat.setElevation(binding.mapView, backDropActive ? 20 : 0);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            activeFilter(general);
        } else {
            binding.enrollmentButton.setVisibility(general ? View.GONE : View.VISIBLE);
        }
    }

    private void activeFilter(boolean general) {
        ConstraintSet initSet = new ConstraintSet();
        initSet.clone(binding.backdropLayout);

        if (backDropActive) {
            initSet.connect(R.id.mainLayout, ConstraintSet.TOP, general ? R.id.filterRecyclerLayout : R.id.formViewContainer, ConstraintSet.BOTTOM, general ? ExtensionsKt.getDp(16) : 0);
        } else {
            initSet.connect(R.id.mainLayout, ConstraintSet.TOP, R.id.backdropGuideTop, ConstraintSet.BOTTOM, 0);
        }

        setFabVisibility(shouldDisplayButton(), !backDropActive || general);
        setCarouselVisibility(backDropActive);
        if (backDropActive) {
            binding.navigationBar.hide();
        } else {
            binding.navigationBar.show();
        }

        initSet.applyTo(binding.backdropLayout);
    }

    @Override
    public void setInitialFilters(List<FilterItem> filtersToDisplay) {
        filtersAdapter.submitList(filtersToDisplay);
    }

    @Override
    public void showClearSearch(boolean empty) {
        showClear.set(empty);
    }

    @Override
    public void hideFilter() {
        binding.searchFilterGeneral.setVisibility(GONE);
    }

    private void setFabVisibility(boolean show, boolean onNavBar) {
        binding.enrollmentButton.animate()
                .setDuration(500)
                .translationX(show ? 0 : 500)
                .translationY(onNavBar ? -ExtensionsKt.getDp(56) : 0)
                .start();

        binding.clearFilterSearchButton.animate()
                .setDuration(500)
                .translationX(show && !onNavBar ? 0 : 500)
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
        OUTreeFragment ouTreeFragment = OUTreeFragment.Companion.newInstance(true, FilterManager.getInstance().getOrgUnitUidsFilters());
        ouTreeFragment.setSelectionCallback(this);
        ouTreeFragment.show(getSupportFragmentManager(), "OUTreeFragment");
    }

    @Override
    public void onSelectionFinished(List<? extends OrganisationUnit> selectedOrgUnits) {
        presenter.setOrgUnitFilters((List<OrganisationUnit>) selectedOrgUnits);
    }

    @Override
    public void showPeriodRequest(Pair<FilterManager.PeriodRequest, Filters> periodRequest) {
        if (periodRequest.getFirst() == FilterManager.PeriodRequest.FROM_TO) {
            DateUtils.getInstance().fromCalendarSelector(this, datePeriod -> {
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
        FilterManager.getInstance().clearWorkingList(true);
        startActivity(TeiDashboardMobileActivity.intent(this, teiUid, enrollmentUid != null ? programUid : null, enrollmentUid));
    }

    @Override
    public void couldNotDownload(String typeName) {
        displayMessage(getString(R.string.download_tei_error, typeName));
    }

    @Override
    public void showBreakTheGlass(String teiUid, String enrollmentUid) {
        new BreakTheGlassBottomDialog()
                .setPositiveButton(reason -> {
                    presenter.downloadTeiWithReason(teiUid, enrollmentUid, reason);
                    return Unit.INSTANCE;
                })
                .show(getSupportFragmentManager(), BreakTheGlassBottomDialog.class.getName());
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
        binding.progressLayout.setVisibility(GONE);
        if (binding.messageContainer.getVisibility() == View.VISIBLE) {
            binding.messageContainer.setVisibility(GONE);
            showMap(true);
        } else {
            SearchMessageResult data = presenter.getMessage(trackerMapData.getTeiModels());
            if (data.getMessage().isEmpty()) {
                binding.messageContainer.setVisibility(GONE);
                binding.mapView.setVisibility(View.VISIBLE);
                binding.mapCarousel.setVisibility(View.VISIBLE);

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
                binding.mapPositionButton.setVisibility(View.VISIBLE);
                animations.endMapLoading(binding.mapCarousel);

            } else {
                binding.messageContainer.setVisibility(View.VISIBLE);
                binding.message.setText(data.getMessage());
                binding.mapView.setVisibility(View.GONE);
                binding.mapCarousel.setVisibility(View.GONE);
                binding.mapLayerButton.setVisibility(View.GONE);
                binding.mapPositionButton.setVisibility(GONE);
            }
            binding.toolbarProgress.hide();
            updateFilters(FilterManager.getInstance().getTotalFilters());
        }
    }

    private void updateCarousel(List<CarouselItemModel> allItems) {
        if (binding.mapCarousel.getAdapter() != null) {
            ((CarouselAdapter) binding.mapCarousel.getAdapter()).setAllItems(allItems);
            ((CarouselAdapter) binding.mapCarousel.getAdapter()).updateLayers(teiMapManager.mapLayerManager.getMapLayers());
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
    /*endregion*/
}
