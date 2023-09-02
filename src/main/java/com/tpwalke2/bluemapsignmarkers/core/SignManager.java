package com.tpwalke2.bluemapsignmarkers.core;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.BlueMapAPIConnector;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.markers.MarkerSetIdentifierCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SignManager {
    public static final SignManager INSTANCE = new SignManager();
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private final BlueMapAPIConnector blueMapAPIConnector;
    private final ActionFactory actionFactory;

    private SignManager() {
        MarkerSetIdentifierCollection markerSetIdentifierCollection = new MarkerSetIdentifierCollection();
        blueMapAPIConnector = new BlueMapAPIConnector(markerSetIdentifierCollection);
        actionFactory = new ActionFactory(markerSetIdentifierCollection);
    }

    public static void addOrUpdate(SignEntry signEntry) {
        INSTANCE.addOrUpdateSign(signEntry);
    }

    private static final String POI_MARKER_SENTINEL = "[map]";

    private final ConcurrentMap<SignEntryKey, SignEntry> signCache = new ConcurrentHashMap<>();

    public List<SignEntry> getAllSigns() {
        return new ArrayList<>(signCache.values());
    }

    public void shutdown() {
        blueMapAPIConnector.shutdown();
    }

    public void addOrUpdateSign(SignEntry signEntry) {
        var key = signEntry.key();
        var existing = signCache.get(key);
        var isPOIMarker = SignEntryHelper.isPOIMarker(signEntry, SignManager.POI_MARKER_SENTINEL);

        var label = SignEntryHelper.getLabel(signEntry, SignManager.POI_MARKER_SENTINEL);
        var detail = SignEntryHelper.getDetail(signEntry, SignManager.POI_MARKER_SENTINEL);

        if (existing == null && isPOIMarker) {
            LOGGER.info("Adding POI marker: {}", signEntry);
            signCache.put(key, signEntry);
            blueMapAPIConnector.dispatch(
                    actionFactory.createAddPOIAction(
                            signEntry.key().x(),
                            signEntry.key().y(),
                            signEntry.key().z(),
                            signEntry.key().parentMap(),
                            label,
                            detail));
            return;
        }

        if (existing != null && !isPOIMarker) {
            LOGGER.info("Removing POI marker: {}", signEntry);
            removeSign(signEntry);
            blueMapAPIConnector.dispatch(
                    actionFactory.createRemovePOIAction(
                            signEntry.key().x(),
                            signEntry.key().y(),
                            signEntry.key().z(),
                            signEntry.key().parentMap()));
        }

        if (existing != null && isPOIMarker) {
            LOGGER.info("Updating POI marker: {}", signEntry);
            signCache.put(key, signEntry);
            blueMapAPIConnector.dispatch(
                    actionFactory.createUpdatePOIAction(
                            signEntry.key().x(),
                            signEntry.key().y(),
                            signEntry.key().z(),
                            signEntry.key().parentMap(),
                            label,
                            detail));
        }
    }

    private void removeSign(SignEntry signEntry) {
        signCache.remove(signEntry.key());
    }
}
