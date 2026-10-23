package ngat.rcs.newstatemodel;

import java.util.*;
import ngat.ems.*;

/** Optional freshness policy over actual EMS measurements. No replacement samples
 * or hardware commands are generated. Invalid/missing timestamps fail closed. */
public final class RequiredWeatherInputs implements MeteorologyStatusUpdateListener {
    private final Map<String,Long> maximumAge = new HashMap<String,Long>();
    private final Map<String,Long> samples = new HashMap<String,Long>();
    private long freshSince;
    private final long recovery;
    private String lastDecision="";
    public RequiredWeatherInputs() {
        String spec=System.getProperty("rcs.weather.required", "");
        for (String entry: spec.split(",")) {
            if (entry.trim().isEmpty()) continue;
            String[] pair=entry.split(":");
            long age=Long.parseLong(pair[1]);
            if (age<=0 || !(pair[0].equals("WMS") || pair[0].equals("CLOUD"))) throw new IllegalArgumentException("Invalid weather requirement");
            maximumAge.put(pair[0],age);
        }
        recovery=Long.getLong("rcs.weather.fresh.recovery.ms",60000L);
        if (recovery<0) throw new IllegalArgumentException("Negative recovery interval");
    }
    public synchronized void meteorologyStatusUpdate(MeteorologyStatus status) {
        String category=status.getCategoryName();
        if (!maximumAge.containsKey(category)) return;
        // Observe any gap before replacing the previous sample.
        ready();
        long stamp=status.getStatusTimeStamp();
        boolean valid=true;
        if (status instanceof CloudStatus) valid=Double.isFinite(((CloudStatus)status).getSkyMinusAmb());
        if (status instanceof WmsStatus) {
            WmsStatus w=(WmsStatus)status;
            valid=Double.isFinite(w.getHumidity()) && Double.isFinite(w.getWindSpeed()) && Double.isFinite(w.getExtTemperature()) &&
                Double.isFinite(w.getMoistureFraction()) && (w.getWmsStatus()==460 || w.getWmsStatus()==464);
        }
        samples.put(category,valid?stamp:0L);
        System.err.println("RCS_WEATHER_SAMPLE category="+category+" sourceTime="+stamp+" valid="+valid);
    }
    public synchronized boolean ready() {
        if (maximumAge.isEmpty()) return true;
        long now=System.currentTimeMillis();
        String missing="";
        for (String cat:maximumAge.keySet()) {
            long time=samples.containsKey(cat)?samples.get(cat):0L;
            if (time<=0 || now-time>maximumAge.get(cat) || time-now>2000) missing+=cat+",";
        }
        if (!missing.isEmpty()) freshSince=0;
        else if (freshSince==0) freshSince=System.nanoTime();
        boolean ready=missing.isEmpty() && System.nanoTime()-freshSince>=recovery*1000000L;
        String decision=ready?"FRESH":missing.isEmpty()?"RECOVERY_HOLD":"UNAVAILABLE:"+missing;
        if (!decision.equals(lastDecision)) {
            System.err.println("RCS_WEATHER_FRESHNESS "+decision+" utc="+now+" samples="+samples);
            lastDecision=decision;
        }
        return ready;
    }
}
