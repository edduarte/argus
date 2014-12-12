package argus.job;

import com.mongodb.*;
import com.mongodb.MongoException.DuplicateKey;
import org.bson.types.ObjectId;
import org.quartz.*;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.Trigger.TriggerState;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.ClassLoadHelper;
import org.quartz.spi.JobStore;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.SchedulerSignaler;
import org.quartz.spi.TriggerFiredBundle;
import org.quartz.spi.TriggerFiredResult;
import org.quartz.utils.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Quartz Scheduler Job Persistence implementation that stores jobs in MongoDB.
 *
 * @author Eduardo Duarte (<a href="mailto:eduardo.miguel.duarte@gmail.com">eduardo.miguel.duarte@gmail.com</a>)
 * @version 1.0
 * @since 1.0
 */
public class MongoDBJobStore implements JobStore {

    private static final Logger logger = LoggerFactory.getLogger(MongoDBJobStore.class);

    private static final String JOB_KEY_NAME = "keyName";
    private static final String JOB_KEY_GROUP = "keyGroup";
    private static final String JOB_KEY_INDEX = "keyIndex";
    private static final String JOB_DESCRIPTION = "jobDescription";
    private static final String JOB_CLASS = "jobClass";
    private static final String TRIGGER_CALENDAR_NAME = "calendarName";
    private static final String TRIGGER_DESCRIPTION = "description";
    private static final String TRIGGER_END_TIME = "endTime";
    private static final String TRIGGER_FINAL_FIRE_TIME = "finalFireTime";
    private static final String TRIGGER_FIRE_INSTANCE_ID = "fireInstanceId";
    private static final String TRIGGER_KEY_NAME = "keyName";
    private static final String TRIGGER_KEY_GROUP = "keyGroup";
    private static final String TRIGGER_KEY_INDEX = "keyIndex";
    private static final String TRIGGER_MISFIRE_INSTRUCTION = "misfireInstruction";
    private static final String TRIGGER_NEXT_FIRE_TIME = "nextFireTime";
    private static final String TRIGGER_PREVIOUS_FIRE_TIME = "previousFireTime";
    private static final String TRIGGER_PRIORITY = "priority";
    private static final String TRIGGER_START_TIME = "startTime";
    private static final String TRIGGER_JOB_ID = "jobId";
    private static final String TRIGGER_CLASS = "class";
    private static final String SIMPLE_TRIGGER_REPEAT_COUNT = "repeatCount";
    private static final String SIMPLE_TRIGGER_REPEAT_INTERVAL = "repeatInterval";
    private static final String SIMPLE_TRIGGER_TIMES_TRIGGERED = "timesTriggered";
    private static final String CRON_TRIGGER_CRON_EXPRESSION = "cronEx";
    private static final String CALENDAR_NAME = "name";
    private static final String CALENDAR_SERIALIZED_OBJECT = "serializedObject";
    private static final String LOCK_KEY_NAME = "keyName";
    private static final String LOCK_KEY_GROUP = "keyGroup";
    private static final String LOCK_INSTANCE_ID = "instanceId";
    private static final String LOCK_TIME = "time";
    protected long misfireThreshold = 5000l;
    private Mongo mongo;
    private String collectionPrefix = "quartz_";
    private String dbName;
    private DBCollection jobCollection;
    private DBCollection triggerCollection;
    private DBCollection calendarCollection;
    private ClassLoadHelper loadHelper;
    private DBCollection locksCollection;
    private String instanceId;
    private String[] addresses;
    private String username;
    private String password;
    private SchedulerSignaler signaler;
    private long triggerTimeoutMillis = 10 * 60 * 1000L;

    @Override
    public void initialize(ClassLoadHelper loadHelper, SchedulerSignaler signaler)
            throws SchedulerConfigException {
        this.loadHelper = loadHelper;
        this.signaler = signaler;

        if (addresses == null || addresses.length == 0) {
            throw new SchedulerConfigException("At least one MongoDB address must be specified.");
        }

        MongoOptions options = new MongoOptions();
        options.safe = true; // need to do this to ensure we get DuplicateKey exceptions

        try {
            ArrayList<ServerAddress> serverAddresses = new ArrayList<>();
            for (String a : addresses) {
                serverAddresses.add(new ServerAddress(a));
            }
            mongo = new Mongo(serverAddresses, options);

        } catch (UnknownHostException | MongoException e) {
            throw new SchedulerConfigException("Could not connect to MongoDB.", e);
        }

        DB db = mongo.getDB(dbName);
        if (username != null) {
            db.authenticate(username, password.toCharArray());
        }
        jobCollection = db.getCollection(collectionPrefix + "jobs");
        triggerCollection = db.getCollection(collectionPrefix + "triggers");
        calendarCollection = db.getCollection(collectionPrefix + "calendars");
        locksCollection = db.getCollection(collectionPrefix + "locks");

        BasicDBObject keys = new BasicDBObject();
        keys.put(JOB_KEY_NAME, 1);
        keys.put(JOB_KEY_GROUP, 1);
        jobCollection.ensureIndex(keys, JOB_KEY_INDEX, true);

        keys = new BasicDBObject();
        keys.put(TRIGGER_KEY_NAME, 1);
        keys.put(TRIGGER_KEY_GROUP, 1);
        triggerCollection.ensureIndex(keys, TRIGGER_KEY_INDEX, true);

        keys = new BasicDBObject();
        keys.put(LOCK_KEY_NAME, 1);
        keys.put(LOCK_KEY_GROUP, 1);
        locksCollection.ensureIndex(keys, null, true);
        // remove all locks for this instance on startup
        locksCollection.remove(new BasicDBObject(LOCK_INSTANCE_ID, instanceId));

        keys = new BasicDBObject();
        keys.put(CALENDAR_NAME, 1);
        calendarCollection.ensureIndex(keys, null, true);
    }

    @Override
    public void schedulerStarted() throws SchedulerException {
    }

    @Override
    public void schedulerPaused() {
    }

    @Override
    public void schedulerResumed() {
    }

    @Override
    public void shutdown() {
        mongo.close();
    }

    @Override
    public boolean supportsPersistence() {
        return true;
    }

    @Override
    public long getEstimatedTimeToReleaseAndAcquireTrigger() {
        // this will vary...
        return 200;
    }

    @Override
    public boolean isClustered() {
        return true;
    }

    @Override
    public void storeJobAndTrigger(JobDetail newJob, OperableTrigger newTrigger)
            throws JobPersistenceException {
        ObjectId jobId = storeJobInMongo(newJob, false);

        logger.debug("Storing job " + newJob.getKey() + " and trigger " + newTrigger.getKey());
        storeTrigger(newTrigger, jobId, false);
    }

    protected void storeTrigger(OperableTrigger newTrigger,
                                ObjectId jobId,
                                boolean replaceExisting)
            throws ObjectAlreadyExistsException {

        BasicDBObject triggerDB = new BasicDBObject();
        triggerDB.put(TRIGGER_CALENDAR_NAME, newTrigger.getCalendarName());
        triggerDB.put(TRIGGER_CLASS, newTrigger.getClass().getName());
        triggerDB.put(TRIGGER_DESCRIPTION, newTrigger.getDescription());
        triggerDB.put(TRIGGER_END_TIME, newTrigger.getEndTime());
        triggerDB.put(TRIGGER_FINAL_FIRE_TIME, newTrigger.getFinalFireTime());
        triggerDB.put(TRIGGER_FIRE_INSTANCE_ID, newTrigger.getFireInstanceId());
        triggerDB.put(TRIGGER_JOB_ID, jobId);
        triggerDB.put(TRIGGER_KEY_NAME, newTrigger.getKey().getName());
        triggerDB.put(TRIGGER_KEY_GROUP, newTrigger.getKey().getGroup());
        triggerDB.put(TRIGGER_MISFIRE_INSTRUCTION, newTrigger.getMisfireInstruction());
        triggerDB.put(TRIGGER_NEXT_FIRE_TIME, newTrigger.getNextFireTime());
        triggerDB.put(TRIGGER_PREVIOUS_FIRE_TIME, newTrigger.getPreviousFireTime());
        triggerDB.put(TRIGGER_PRIORITY, newTrigger.getPriority());
        triggerDB.put(TRIGGER_START_TIME, newTrigger.getStartTime());

        if (newTrigger instanceof SimpleTrigger) {
            SimpleTrigger simple = (SimpleTrigger) newTrigger;
            triggerDB.put(SIMPLE_TRIGGER_REPEAT_COUNT, simple.getRepeatCount());
            triggerDB.put(SIMPLE_TRIGGER_REPEAT_INTERVAL, simple.getRepeatInterval());
            triggerDB.put(SIMPLE_TRIGGER_TIMES_TRIGGERED, simple.getTimesTriggered());
        }
        if (newTrigger instanceof CronTrigger) {
            //
            // for CronTrigger, it need to record its cron expression. Without that, the next_fire_time cannot be
            // calculated. It cause the trigger being deleted after its first fire.
            //
            CronTrigger simple = (CronTrigger) newTrigger;
            triggerDB.put(CRON_TRIGGER_CRON_EXPRESSION, simple.getCronExpression());
        }
        try {
            triggerCollection.insert(triggerDB);
        } catch (DuplicateKey key) {
            if (replaceExisting) {
                triggerDB.remove("_id");
                triggerCollection.update(keyAsDBObject(newTrigger.getKey()), triggerDB);
            } else {
                throw new ObjectAlreadyExistsException(newTrigger);
            }
        }
    }

    @Override
    public void storeJob(JobDetail newJob, boolean replaceExisting)
            throws JobPersistenceException {
        storeJobInMongo(newJob, replaceExisting);
    }

    protected ObjectId storeJobInMongo(JobDetail newJob, boolean replaceExisting)
            throws ObjectAlreadyExistsException {
        JobKey key = newJob.getKey();

        BasicDBObject job = keyAsDBObject(key);

        if (replaceExisting) {
            BasicDBObject result = (BasicDBObject) jobCollection.findOne(job);
            if (result != null) {
                job = result;
            }
        }

        job.put(JOB_KEY_NAME, key.getName());
        job.put(JOB_KEY_GROUP, key.getGroup());
        job.put(JOB_DESCRIPTION, newJob.getDescription());
        job.put(JOB_CLASS, newJob.getJobClass().getName());

        job.putAll(newJob.getJobDataMap());

        jobCollection.insert(job);

        return (ObjectId) job.get("_id");
    }

    protected BasicDBObject keyAsDBObject(Key key) {
        BasicDBObject job = new BasicDBObject();
        job.put(JOB_KEY_NAME, key.getName());
        job.put(JOB_KEY_GROUP, key.getGroup());
        return job;
    }

    @Override
    public void storeJobsAndTriggers(Map<JobDetail, Set<? extends Trigger>> triggersAndJobs, boolean replace)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeJob(JobKey jobKey) throws JobPersistenceException {
        BasicDBObject keyObject = keyAsDBObject(jobKey);
        DBCursor find = jobCollection.find(keyObject);
        if (find.hasNext()) {
            DBObject jobObj = find.next();
            jobCollection.remove(keyObject);
            triggerCollection.remove(new BasicDBObject(TRIGGER_JOB_ID, jobObj.get("_id")));

            return true;
        }

        return false;
    }

    @Override
    public boolean removeJobs(List<JobKey> jobKeys) throws JobPersistenceException {
        for (JobKey key : jobKeys) {
            removeJob(key);
        }
        return false;
    }

    private JobDetail retrieveJob(OperableTrigger trigger) throws JobPersistenceException {
        try {
            return retrieveJob(trigger.getJobKey());
        } catch (JobPersistenceException e) {
            removeTriggerLock(trigger);
            throw e;
        }
    }

    @Override
    public JobDetail retrieveJob(JobKey jobKey) throws JobPersistenceException {
        DBObject dbObject = retrieveJobDBObject(jobKey);

        try {
            Class<Job> jobClass = (Class<Job>) loadHelper.getClassLoader().loadClass((String) dbObject.get(JOB_CLASS));

            JobBuilder builder = JobBuilder.newJob(jobClass)
                    .withIdentity((String) dbObject.get(JOB_KEY_NAME), (String) dbObject.get(JOB_KEY_GROUP))
                    .withDescription((String) dbObject.get(JOB_KEY_NAME));

            JobDataMap jobData = new JobDataMap();
            for (String key : dbObject.keySet()) {
                if (!key.equals(JOB_KEY_NAME)
                        && !key.equals(JOB_KEY_GROUP)
                        && !key.equals(JOB_CLASS)
                        && !key.equals(JOB_DESCRIPTION)
                        && !key.equals("_id")) {
                    jobData.put(key, dbObject.get(key));
                }
            }

            return builder.usingJobData(jobData).build();
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Could not load job class " + dbObject.get(JOB_CLASS), e);
        }
    }

    protected DBObject retrieveJobDBObject(JobKey jobKey) {
        return jobCollection.findOne(keyAsDBObject(jobKey));
    }

    @Override
    public void storeTrigger(OperableTrigger newTrigger, boolean replaceExisting)
            throws JobPersistenceException {
        if (newTrigger.getJobKey() == null) {
            throw new JobPersistenceException("Trigger must be associated with a job. Please specify a JobKey.");
        }

        DBObject dbObject = jobCollection.findOne(keyAsDBObject(newTrigger.getJobKey()));
        if (dbObject != null) {
            storeTrigger(newTrigger, (ObjectId) dbObject.get("_id"), replaceExisting);
        } else {
            throw new JobPersistenceException("Could not find job with key " + newTrigger.getJobKey());
        }
    }

    @Override
    public boolean removeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        BasicDBObject dbObject = keyAsDBObject(triggerKey);
        DBCursor find = triggerCollection.find(dbObject);
        if (find.count() > 0) {
            triggerCollection.remove(dbObject);

            return true;
        }

        return false;
    }

    @Override
    public boolean removeTriggers(List<TriggerKey> triggerKeys) throws JobPersistenceException {
        for (TriggerKey key : triggerKeys) {
            removeTrigger(key);
        }
        return false;
    }

    @Override
    public boolean replaceTrigger(TriggerKey triggerKey, OperableTrigger newTrigger) throws JobPersistenceException {
        removeTrigger(triggerKey);
        storeTrigger(newTrigger, false);
        return true;
    }

    @Override
    public OperableTrigger retrieveTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        DBObject dbObject = triggerCollection.findOne(keyAsDBObject(triggerKey));
        if (dbObject == null) {
            return null;
        }
        return toTrigger(triggerKey, dbObject);
    }

    protected OperableTrigger toTrigger(TriggerKey triggerKey, DBObject dbObject)
            throws JobPersistenceException {
        OperableTrigger trigger;
        try {
            Class<OperableTrigger> triggerClass = (Class<OperableTrigger>) loadHelper
                    .getClassLoader().loadClass((String) dbObject.get(TRIGGER_CLASS));
            trigger = triggerClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new JobPersistenceException("Could not find trigger class "
                    + dbObject.get(TRIGGER_CLASS));
        } catch (Exception e) {
            throw new JobPersistenceException("Could not instantiate trigger class "
                    + dbObject.get(TRIGGER_CLASS));
        }

        trigger.setKey(triggerKey);
        trigger.setCalendarName((String) dbObject.get(TRIGGER_CALENDAR_NAME));
        trigger.setDescription((String) dbObject.get(TRIGGER_DESCRIPTION));
        trigger.setEndTime((Date) dbObject.get(TRIGGER_END_TIME));
        trigger.setFireInstanceId((String) dbObject.get(TRIGGER_FIRE_INSTANCE_ID));
        trigger.setMisfireInstruction((Integer) dbObject.get(TRIGGER_MISFIRE_INSTRUCTION));
        trigger.setNextFireTime((Date) dbObject.get(TRIGGER_NEXT_FIRE_TIME));
        trigger.setPreviousFireTime((Date) dbObject.get(TRIGGER_PREVIOUS_FIRE_TIME));
        trigger.setPriority((Integer) dbObject.get(TRIGGER_PRIORITY));
        trigger.setStartTime((Date) dbObject.get(TRIGGER_START_TIME));

        if (trigger instanceof CronTriggerImpl) {
            CronTriggerImpl simple = (CronTriggerImpl) trigger;
            Object cronEx = dbObject.get(CRON_TRIGGER_CRON_EXPRESSION);
            if (cronEx != null) {
                try {
                    simple.setCronExpression((String) cronEx);
                } catch (ParseException e) {
                    throw new JobPersistenceException("Could not populate cron expression " + cronEx);
                }
            }
        }
        if (trigger instanceof SimpleTriggerImpl) {
            SimpleTriggerImpl simple = (SimpleTriggerImpl) trigger;
            Object repeatCount = dbObject.get(SIMPLE_TRIGGER_REPEAT_COUNT);
            if (repeatCount != null) {
                simple.setRepeatCount((Integer) repeatCount);
            }
            Object repeatInterval = dbObject.get(SIMPLE_TRIGGER_REPEAT_INTERVAL);
            if (repeatInterval != null) {
                simple.setRepeatInterval((Long) repeatInterval);
            }
            Object timesTriggered = dbObject.get(SIMPLE_TRIGGER_TIMES_TRIGGERED);
            if (timesTriggered != null) {
                simple.setTimesTriggered((Integer) timesTriggered);
            }
        }
        DBObject job = jobCollection.findOne(
                new BasicDBObject("_id", dbObject.get(TRIGGER_JOB_ID)));
        if (job != null) {
            trigger.setJobKey(new JobKey(
                    (String) job.get(JOB_KEY_NAME),
                    (String) job.get(JOB_KEY_GROUP)
            ));
            return trigger;
        } else {
            // job was deleted
            return null;
        }
    }

    @Override
    public boolean checkExists(JobKey jobKey) throws JobPersistenceException {
        return jobCollection.find(keyAsDBObject(jobKey)).count() > 0;
    }

    @Override
    public boolean checkExists(TriggerKey triggerKey) throws JobPersistenceException {
        return triggerCollection.find(keyAsDBObject(triggerKey)).count() > 0;
    }

    @Override
    public void clearAllSchedulingData() throws JobPersistenceException {
        jobCollection.remove(new BasicDBObject());
        triggerCollection.remove(new BasicDBObject());
        calendarCollection.remove(new BasicDBObject());
    }

    @Override
    public void storeCalendar(String name,
                              Calendar calendar,
                              boolean replaceExisting,
                              boolean updateTriggers)
            throws JobPersistenceException {
        if (updateTriggers) {
            throw new UnsupportedOperationException("Updating triggers is not supported.");
        }

        BasicDBObject dbObject = new BasicDBObject();
        dbObject.put(CALENDAR_NAME, name);
        dbObject.put(CALENDAR_SERIALIZED_OBJECT, serialize(calendar));

        calendarCollection.insert(dbObject);
    }

    private Object serialize(Calendar calendar) throws JobPersistenceException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(calendar);
            objectStream.close();
            return byteStream.toByteArray();
        } catch (IOException e) {
            throw new JobPersistenceException("Could not serialize Calendar.", e);
        }
    }

    @Override
    public boolean removeCalendar(String calName) throws JobPersistenceException {
        BasicDBObject searchObj = new BasicDBObject(CALENDAR_NAME, calName);
        if (calendarCollection.find(searchObj).count() > 0) {
            calendarCollection.remove(searchObj);
            return true;
        }
        return false;
    }

    @Override
    public Calendar retrieveCalendar(String calName) throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getNumberOfJobs() throws JobPersistenceException {
        return (int) jobCollection.count();
    }

    @Override
    public int getNumberOfTriggers() throws JobPersistenceException {
        return (int) triggerCollection.count();
    }

    @Override
    public int getNumberOfCalendars() throws JobPersistenceException {
        return calendarCollection.find().count();
    }

    @Override
    public Set<JobKey> getJobKeys(GroupMatcher<JobKey> matcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<TriggerKey> getTriggerKeys(GroupMatcher<TriggerKey> matcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getJobGroupNames() throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getTriggerGroupNames() throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getCalendarNames() throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<OperableTrigger> getTriggersForJob(JobKey jobKey)
            throws JobPersistenceException {
        DBObject dbObject = retrieveJobDBObject(jobKey);

        List<OperableTrigger> triggers = new ArrayList<>();
        DBCursor cursor = triggerCollection.find(
                new BasicDBObject(TRIGGER_JOB_ID, dbObject.get("_id")));
        while (cursor.hasNext()) {
            triggers.add(toTrigger(cursor.next()));
        }

        return triggers;
    }

    @Override
    public TriggerState getTriggerState(TriggerKey triggerKey)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pauseTrigger(TriggerKey triggerKey)
            throws JobPersistenceException {
    }

    @Override
    public Collection<String> pauseTriggers(GroupMatcher<TriggerKey> matcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pauseJob(JobKey jobKey) throws JobPersistenceException {
    }

    @Override
    public Collection<String> pauseJobs(GroupMatcher<JobKey> groupMatcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeTrigger(TriggerKey triggerKey) throws JobPersistenceException {
        logger.debug("Resume trigger" + triggerKey);
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> resumeTriggers(GroupMatcher<TriggerKey> matcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getPausedTriggerGroups() throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeJob(JobKey jobKey) throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> resumeJobs(GroupMatcher<JobKey> matcher)
            throws JobPersistenceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pauseAll() throws JobPersistenceException {
    }

    @Override
    public void resumeAll() throws JobPersistenceException {
    }

    @Override
    public List<OperableTrigger> acquireNextTriggers(long noLaterThan,
                                                     int maxCount,
                                                     long timeWindow)
            throws JobPersistenceException {

        BasicDBObject query = new BasicDBObject();
        query.put(TRIGGER_NEXT_FIRE_TIME, new BasicDBObject("$lte", new Date(noLaterThan)));

        if (logger.isDebugEnabled()) {
            logger.debug("Finding up to " + maxCount
                    + " triggers which have time less than " + new Date(noLaterThan));
        }
        List<OperableTrigger> triggers = new ArrayList<>();
        DBCursor cursor = triggerCollection.find(query);

        BasicDBObject sort = new BasicDBObject();
        sort.put(TRIGGER_NEXT_FIRE_TIME, Integer.valueOf(1));
        cursor.sort(sort);

        if (logger.isDebugEnabled()) {
            logger.debug("Found " + cursor.count() + " triggers which are eligible to be run.");
        }

        while (cursor.hasNext() && maxCount > triggers.size()) {
            DBObject dbObj = cursor.next();

            BasicDBObject lock = new BasicDBObject();
            lock.put(LOCK_KEY_NAME, dbObj.get(TRIGGER_KEY_NAME));
            lock.put(LOCK_KEY_GROUP, dbObj.get(TRIGGER_KEY_GROUP));
            lock.put(LOCK_INSTANCE_ID, instanceId);
            lock.put(LOCK_TIME, new Date());

            try {
                OperableTrigger trigger = toTrigger(dbObj);

                if (trigger.getNextFireTime() == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Skipping trigger " + trigger.getKey()
                                + " as it has no next fire time.");
                    }

                    continue;
                }

                // deal with misfires
                if (applyMisfire(trigger) && trigger.getNextFireTime() == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Skipping trigger " + trigger.getKey()
                                + " as it has no next fire time after the misfire was applied.");
                    }

                    continue;
                }
                logger.debug("Inserting lock for trigger " + trigger.getKey());
                locksCollection.insert(lock);
                logger.debug("Aquired trigger " + trigger.getKey());
                triggers.add(trigger);
            } catch (DuplicateKey e) {

                OperableTrigger trigger = toTrigger(dbObj);

                // someone else acquired this lock, so ignore it
                logger.debug("Failed to acquire trigger " + trigger.getKey() + " due to a lock");

                lock = new BasicDBObject();
                lock.put(LOCK_KEY_NAME, dbObj.get(TRIGGER_KEY_NAME));
                lock.put(LOCK_KEY_GROUP, dbObj.get(TRIGGER_KEY_GROUP));

                DBObject existingLock;
                DBCursor lockCursor = locksCollection.find(lock);
                if (lockCursor.hasNext()) {
                    existingLock = lockCursor.next();
                } else {
                    logger.error("Error retrieving expired lock from the database. Maybe it was deleted");
                    return acquireNextTriggers(noLaterThan, maxCount, timeWindow);
                }

                // support for trigger lock expiration
                if (isTriggerLockExpired(existingLock)) {
                    logger.error("Lock for trigger " + trigger.getKey()
                            + " is expired - removing lock and retrying trigger acquisition");
                    removeTriggerLock(trigger);
                    return acquireNextTriggers(noLaterThan, maxCount, timeWindow);
                }
            }
        }

        return triggers;
    }

    protected boolean isTriggerLockExpired(DBObject lock) {
        Date lockTime = (Date) lock.get(LOCK_TIME);
        long elapsedTime = System.currentTimeMillis() - lockTime.getTime();
        return (elapsedTime > triggerTimeoutMillis);
    }

    protected boolean applyMisfire(OperableTrigger trigger) throws JobPersistenceException {
        long misfireTime = System.currentTimeMillis();
        if (getMisfireThreshold() > 0) {
            misfireTime -= getMisfireThreshold();
        }

        Date tnft = trigger.getNextFireTime();
        if (tnft == null || tnft.getTime() > misfireTime ||
                trigger.getMisfireInstruction() == Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY) {
            return false;
        }

        Calendar cal = null;
        if (trigger.getCalendarName() != null) {
            cal = retrieveCalendar(trigger.getCalendarName());
        }

        signaler.notifyTriggerListenersMisfired((OperableTrigger) trigger.clone());

        trigger.updateAfterMisfire(cal);

        if (trigger.getNextFireTime() == null) {
            signaler.notifySchedulerListenersFinalized(trigger);
        } else if (tnft.equals(trigger.getNextFireTime())) {
            return false;
        }

        storeTrigger(trigger, true);
        return true;
    }

    protected OperableTrigger toTrigger(DBObject dbObj) throws JobPersistenceException {
        TriggerKey key = new TriggerKey(
                (String) dbObj.get(TRIGGER_KEY_NAME),
                (String) dbObj.get(TRIGGER_KEY_GROUP)
        );
        return toTrigger(key, dbObj);
    }

//    public void releaseAcquiredTrigger(OperableTrigger trigger) throws JobPersistenceException {
//        try {
//            removeTriggerLock(trigger);
//        } catch (Exception e) {
//            throw new JobPersistenceException(e.getLocalizedMessage(),e);
//        }
//    }


    @Override
    public void releaseAcquiredTrigger(OperableTrigger trigger) {
        removeTriggerLock(trigger);
    }

    @Override
    public List<TriggerFiredResult> triggersFired(List<OperableTrigger> triggers)
            throws JobPersistenceException {

        List<TriggerFiredResult> results = new ArrayList<>();

        for (OperableTrigger trigger : triggers) {
            logger.debug("Fired trigger " + trigger.getKey());
            Calendar cal = null;
            if (trigger.getCalendarName() != null) {
                cal = retrieveCalendar(trigger.getCalendarName());
                if (cal == null)
                    continue;
            }

            trigger.triggered(cal);
            storeTrigger(trigger, true);

            Date prevFireTime = trigger.getPreviousFireTime();

            TriggerFiredBundle bndle = new TriggerFiredBundle(retrieveJob(
                    trigger), trigger, cal,
                    false, new Date(), trigger.getPreviousFireTime(), prevFireTime,
                    trigger.getNextFireTime());

            JobDetail job = bndle.getJobDetail();

            if (job.isConcurrentExectionDisallowed()) {
                throw new UnsupportedOperationException("ConcurrentExecutionDisallowed "
                        + "is not supported currently.");
            }

            results.add(new TriggerFiredResult(bndle));
        }
        return results;
    }

    @Override
    public void triggeredJobComplete(OperableTrigger trigger,
                                     JobDetail jobDetail,
                                     CompletedExecutionInstruction triggerInstCode) {

        logger.debug("Trigger completed " + trigger.getKey());
        // check for trigger deleted during execution...
        OperableTrigger trigger2 = null;
        try {
            trigger2 = retrieveTrigger(trigger.getKey());
        } catch (JobPersistenceException e) {
            logger.error(e.getMessage(), e);
            return;
        }
        if (trigger2 != null) {
            if (triggerInstCode == CompletedExecutionInstruction.DELETE_TRIGGER) {
                if (trigger.getNextFireTime() == null) {
                    // double check for possible reschedule within job
                    // execution, which would cancel the need to delete...
                    if (trigger2.getNextFireTime() == null) {
                        try {
                            removeTrigger(trigger.getKey());
                        } catch (JobPersistenceException ex) {
                            logger.error(ex.getMessage(), ex);
                            return;
                        }
                    }
                } else {
                    try {
                        removeTrigger(trigger.getKey());
                    } catch (JobPersistenceException ex) {
                        logger.error(ex.getMessage(), ex);
                        return;
                    }
                    signaler.signalSchedulingChange(0L);
                }
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_TRIGGER_COMPLETE) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_TRIGGER_ERROR) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L);
            } else if (triggerInstCode == CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_COMPLETE) {
                // TODO: need to store state
                signaler.signalSchedulingChange(0L);
            }
        }

        removeTriggerLock(trigger);
    }

    protected void removeTriggerLock(OperableTrigger trigger) {
        logger.debug("Removing trigger lock " + trigger.getKey() + "." + instanceId);
        BasicDBObject lock = new BasicDBObject();
        lock.put(LOCK_KEY_NAME, trigger.getKey().getName());
        lock.put(LOCK_KEY_GROUP, trigger.getKey().getGroup());
        lock.put(LOCK_INSTANCE_ID, instanceId);

        locksCollection.remove(lock);
        logger.debug("Trigger lock " + trigger.getKey() + "." + instanceId + " removed.");
    }

    @Override
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public void setInstanceName(String scheduleName) {
    }

    @Override
    public void setThreadPoolSize(int poolSize) {
    }

    public void setAddresses(String addresses) {
        this.addresses = addresses.split(",");
    }

    public DBCollection getJobCollection() {
        return jobCollection;
    }

    public DBCollection getTriggerCollection() {
        return triggerCollection;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public void setCollectionPrefix(String prefix) {
        collectionPrefix = prefix + "_";
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getMisfireThreshold() {
        return misfireThreshold;
    }

    public void setMisfireThreshold(long misfireThreshold) {
        this.misfireThreshold = misfireThreshold;
    }

    public void setTriggerTimeoutMillis(long triggerTimeoutMillis) {
        this.triggerTimeoutMillis = triggerTimeoutMillis;
    }

}