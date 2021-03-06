package org.dhis2.usescases.teiDashboard.nfcdata;

import org.dhis2.commons.di.dagger.PerActivity;
import org.dhis2.data.qr.QRCodeGenerator;
import org.dhis2.data.qr.QRInterface;
import org.hisp.dhis.android.core.D2;

import dagger.Module;
import dagger.Provides;

/**
 * QUADRAM. Created by ppajuelo on 19/12/2017.
 */

@Module
public class NfcDataWriteModule {

    @Provides
    @PerActivity
    QRInterface providesQRInterface(D2 d2) {
        return new QRCodeGenerator(d2);
    }
}
