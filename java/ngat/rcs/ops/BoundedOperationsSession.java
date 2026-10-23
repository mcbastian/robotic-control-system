package ngat.rcs.ops;

import ngat.rcs.RCS_Controller;
import ngat.rcs.newstatemodel.*;
import ngat.rcs.tms.TaskOperations;

/** Optional, process-local admission budget. No persisted session is ever resumed.
 * This limits SOCA requests, including failed selections, not only successful groups.
 * It does not select a mode, a group, or override any readiness/weather decision. */
public final class BoundedOperationsSession {
    private static boolean armed, engineeringActive;
    private static final java.util.Set<String> used = new java.util.HashSet<String>();
    private static String id = "";
    private static int limit, admitted;
    private static long deadline;
    private static ngat.rcs.tms.Task settling;
    public static boolean required() { return Boolean.getBoolean("rcs.session.required"); }
    public static synchronized boolean enterEngineering() {
        if (armed || engineeringActive) return false;
        engineeringActive=true; return true;
    }
    public static synchronized void leaveEngineering() { engineeringActive=false; }
    public static synchronized boolean isArmed() { return armed; }
    private static boolean settled() {
        return settling == null || settling.isAborted() || settling.isDone() || settling.isFailed();
    }
    public static synchronized String status() {
        return "id="+id+" armed="+armed+" admitted="+admitted+" limit="+limit+" settled="+settled();
    }
    public static synchronized void arm(String name, int count, int seconds) throws Exception {
        if (!required() || armed || engineeringActive || used.contains(name) || !name.matches("[A-Za-z0-9_]{8,80}") || count<1 || count>10 || seconds<1 || seconds>7200)
            throw new IllegalArgumentException("Invalid or overlapping bounded session");
        if (!settled())
            throw new IllegalStateException("Previous operations task is still terminating");
        if (TaskOperations.getInstance().getOperationsManager().getOperationsState()!=OperationsManager.OP_STATE_IDLE ||
            !RCS_Controller.controller.getTestStateModel().getState().getStateName().equals("INIT"))
            throw new IllegalStateException("Previous operations have not returned to engineering idle");
        used.add(name); id=name; limit=count; admitted=0; deadline=System.nanoTime()+seconds*1000000000L; armed=true;
        System.err.println("RCS_SESSION ARMED "+status()+" seconds="+seconds);
        RCS_Controller.controller.getTestStateModel().environmentChanged(new EnvironmentChangeEvent(EnvironmentChangeEvent.INTENT_OPERATIONAL));
    }
    public static synchronized String admit() {
        if (!required()) return "";
        if (!armed || System.nanoTime()>=deadline || admitted>=limit) return null;
        admitted++;
        String name=id+"/request-"+admitted;
        System.err.println("RCS_SESSION ADMITTED "+name);
        return name;
    }
    public static synchronized void sequenceEnded() {
        if (required() && armed && admitted>=limit) stop("request budget consumed");
    }
    public static synchronized void tick() {
        if (armed && System.nanoTime()>=deadline) stop("elapsed limit");
    }
    public static synchronized void stop(String reason) {
        if (!armed) return;
        settling=TaskOperations.getInstance().getOperationsManager().getCurrentOperationsTask();
        armed=false; // close admission before asynchronous cancellation
        System.err.println("RCS_SESSION DISARMED "+status()+" reason="+reason);
        try {
            RCS_Controller.controller.getTestStateModel().environmentChanged(new EnvironmentChangeEvent(EnvironmentChangeEvent.INTENT_ENGINEERING));
            TaskOperations.getInstance().getOperationsManager().performAction(
                new FastAbortAction(AbortAction.ENG_REQUEST,"Bounded session: "+reason), null);
        } catch (Exception e) { throw new IllegalStateException("Unable to terminate operations",e); }
    }
}
