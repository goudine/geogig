package org.locationtech.geogig.data;

/**
 * Created by mthompson on 2017-01-11.
 */
import static com.google.common.base.Optional.absent;

import java.util.Collection;
import java.util.Set;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

public class EPSGBoundsCalc {

//    public EPSGBoundsCalc() {
//    }

    public Optional<Envelope> getExtents(CoordinateReferenceSystem crs,
        StringBuilder outErr) {
        final Extent domainOfValidity = crs.getDomainOfValidity();
        if (null == domainOfValidity) {
            outErr.append("No domain of validity provided by CRS definition");
            return absent();
        }
        Collection<? extends GeographicExtent> geographicElements;
        geographicElements = domainOfValidity.getGeographicElements();

        if (null == geographicElements || geographicElements.size() != 1) {
            outErr.append("Number of geographic elements != 1");
            return absent();
        }

        GeographicExtent geographicExtent = geographicElements.iterator().next();
        if (!(geographicExtent instanceof GeographicBoundingBox)) {
            outErr.append("geographic extent is not a geographic bounding box");
            return absent();
        }

        if (!geographicExtent.getInclusion()) {
            outErr.append("geographic extent is exclusive, can only deal with inclusive ones");
            return absent();
        }

        GeographicBoundingBox geographicBoundingBox = (GeographicBoundingBox) geographicExtent;

        double minx = geographicBoundingBox.getWestBoundLongitude();
        double miny = geographicBoundingBox.getSouthBoundLatitude();
        double maxx = geographicBoundingBox.getEastBoundLongitude();
        double maxy = geographicBoundingBox.getNorthBoundLatitude();

        //transforms into wgs84 from crs in calc()
        CoordinateReferenceSystem wgs84LongFirst;
        CoordinateReferenceSystem targetCRS;

        try {
            wgs84LongFirst = CRS.decode("EPSG:4326", true);
            targetCRS = CRS.decode("EPSG:3412");
            MathTransform mathTransform = CRS.findMathTransform(wgs84LongFirst, crs, true);
            //create envelope of input coords
            Envelope wgs84Envelope = new Envelope(minx, maxx, miny, maxy);
            //transform to wgs84, JTS transform doesn't work for polar
            ReferencedEnvelope refBounds = new ReferencedEnvelope(wgs84Envelope, wgs84LongFirst);
            Envelope transformBounds = refBounds.transform(targetCRS, true);
            return Optional.of(transformBounds);

        } catch (Exception e) {
            outErr.append("ERROR: " + e.getMessage());
            return absent();
        }
    }

    //change so we can lookup a particular CRS, currently iterates through all`
    public Optional<Envelope> findCode(CoordinateReferenceSystem crs) throws Exception {
        Optional<Envelope> projectionBounds = Optional.absent();
        CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory(true);
        Set<String> authorityCodes = authorityFactory
            .getAuthorityCodes(CoordinateReferenceSystem.class);
        for (String code : authorityCodes) {
            //only checks the EPSG codes
            if (code.startsWith("EPSG:") && code.contains(crs.getName().toString())) {
                CoordinateReferenceSystem crs_temp;
                try {
                    crs = authorityFactory.createCoordinateReferenceSystem(code);
                } catch (Exception e) {
                    System.err.printf("%s: Unable to create CRS: %s\n", code, e.getMessage());
                    continue;
                }
                StringBuilder err = new StringBuilder();
                projectionBounds = getExtents(crs, err);
                System.err.printf("%s: %s %s\n", code, projectionBounds.orNull(), err);
            }
        }
        return projectionBounds;
    }
}
