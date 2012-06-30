package info.staticfree.android.twentyfourhour;

/*
 * Copyright (C) 2011-2012 Steve Pomeroy <steve@staticfree.info>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import info.staticfree.android.twentyfourhour.Analog24HClock.DialOverlay;

import java.util.Calendar;
import java.util.TimeZone;

import uk.me.jstott.coordconv.LatitudeLongitude;
import uk.me.jstott.sun.Sun;
import uk.me.jstott.sun.Time;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

public class SunPositionOverlay implements DialOverlay {

	private static final String TAG = SunPositionOverlay.class.getSimpleName();

	private final LocationManager mLm;

	private final RectF inset = new RectF();
	private final LatitudeLongitude ll = new LatitudeLongitude(0,0);

	private Location mLocation;

	private static Paint OVERLAY_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG) ;
	private static Paint OVERLAY_NO_INFO_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

	static {
		OVERLAY_PAINT.setARGB(50, 0, 0, 0);
		OVERLAY_PAINT.setStyle(Paint.Style.FILL);

	}

	public SunPositionOverlay(Context context){
		mLm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		OVERLAY_NO_INFO_PAINT.setShader(new BitmapShader(BitmapFactory.decodeResource(
				context.getResources(), R.drawable.no_sunrise_sunset_tile), Shader.TileMode.REPEAT,
				Shader.TileMode.REPEAT));
	}

	private Location getRecentLocation(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO){
			return mLm.getLastKnownLocation("passive");

		}else{
			Location bestLoc = null;
			long mostRecent = 0;
			for (final String p : mLm.getProviders(false)){
				final Location l = mLm.getLastKnownLocation(p);
				if (l == null){
					continue;
				}
				final long fixTime = l.getTime();
				if (bestLoc == null){
					bestLoc = l;
					mostRecent = fixTime;
				}else{
					if (fixTime > mostRecent){
						bestLoc = l;
						mostRecent = fixTime;
					}
				}
			}
			return bestLoc;
		}
	}

	public void setLocation(Location location) {
		mLocation = location;
	}

	public void setUsePassiveLocation() {
		mLocation = null;
	}

	private float getHourArcAngle(int h, int m){
		return (Analog24HClock.getHourHandAngle(h, m) + 270) % 360.0f;
	}

	private void drawPlaceholder(Canvas canvas) {
		canvas.drawArc(inset, 0, 180, true, OVERLAY_NO_INFO_PAINT);
	}

	@Override
	public void onDraw(Canvas canvas, int cX, int cY, int w, int h, Calendar calendar) {
		final Location loc = mLocation != null ? mLocation : getRecentLocation();
		final int insetW = (int) (w / 2.0f / 2.0f);
		final int insetH = (int) (h / 2.0f / 2.0f);
		inset.set(cX - insetW, cY - insetH, cX + insetW, cY + insetH);

		if (loc == null){
			// not much we can do if we don't have a location
			drawPlaceholder(canvas);
			return;
		}
		ll.setLatitude(loc.getLatitude());
		ll.setLongitude(loc.getLongitude());

		final TimeZone tz = calendar.getTimeZone();

		final boolean dst = calendar.get(Calendar.DST_OFFSET) != 0;

		try {
			final Time sunrise = Sun.sunriseTime(calendar, ll, tz, dst);
			final float sunriseAngle = getHourArcAngle(sunrise.getHours(), sunrise.getMinutes());

			final Time sunset = Sun.sunsetTime(calendar, ll, tz, dst);
			final float sunsetAngle = getHourArcAngle(sunset.getHours(), sunset.getMinutes());

			canvas.drawArc(inset, sunsetAngle, (360 + (sunriseAngle - sunsetAngle)) % 360, true,
					OVERLAY_PAINT);

			// this can happen when lat/lon and the timezone are out of sync, causing impossible
			// sunrise/sunset times to be calculated.
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "Error computing sunrise / sunset time", e);
			drawPlaceholder(canvas);
		}
	}
}
