package net.kenevans.heartmonitor;

import java.io.File;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Simple database access helper class. Defines the basic CRUD operations for
 * Notes, and gives the ability to list all notes as well as retrieve or modify
 * a specific entry.
 * 
 * This has been improved from the first version of this tutorial through the
 * addition of better error handling and also using returning a Cursor instead
 * of using a collection of inner classes (which is less scalable and not
 * recommended).
 */
public class HeartMonitorDbAdapter implements IConstants {
	private DatabaseHelper mDbHelper;
	private SQLiteDatabase mDb;

	/** Database creation SQL statement */
	private static final String DB_CREATE = "create table " + DB_TABLE
			+ " (_id integer primary key autoincrement, " + COL_DATE
			+ " integer not null, " + COL_DATEMOD + " integer not null, "
			+ COL_COUNT + " integer not null, " + COL_TOTAL
			+ " integer not null, " + COL_EDITED + " integer not null,"
			+ COL_COMMENT + " text not null);";

	private final Context mCtx;

	/**
	 * Constructor - takes the context to allow the database to be
	 * opened/created
	 * 
	 * @param ctx
	 *            the Context within which to work
	 */
	public HeartMonitorDbAdapter(Context ctx) {
		this.mCtx = ctx;
	}

	/**
	 * Open the notes database. If it cannot be opened, try to create a new
	 * instance of the database. If it cannot be created, throw an exception to
	 * signal the failure
	 * 
	 * @return this (self reference, allowing this to be chained in an
	 *         initialization call)
	 * @throws SQLException
	 *             if the database could be neither opened or created
	 */
	public HeartMonitorDbAdapter open() throws SQLException {
		// Make sure the directory exists and is available
		File dir = HeartMonitorActivity.getDatabaseDirectory();
		if (dir == null) {
			Utils.errMsg(mCtx, "Cannot access database on SD card");
			return null;
		}
		if (!dir.exists()) {
			dir.mkdirs();
			// Try again
			if (!dir.exists()) {
				Utils.errMsg(mCtx, "Unable to create directory on SD card");
				return null;
			}
		}
		mDbHelper = new DatabaseHelper(dir.getPath());
		mDb = mDbHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDbHelper.close();
	}

	/**
	 * Create new data using the parameters provided. If the data is
	 * successfully created return the new rowId for that entry, otherwise
	 * return a -1 to indicate failure.
	 * 
	 * @param date
	 * @param dateMod
	 * @param count
	 * @param total
	 * @param edited
	 * @param comment
	 * @return rowId or -1 on failure.
	 */
	public long createData(long date, long dateMod, long count, long total,
			boolean edited, String comment) {
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_DATEMOD, dateMod);
		values.put(COL_COUNT, count);
		values.put(COL_TOTAL, total);
		values.put(COL_EDITED, edited);
		values.put(COL_COMMENT, comment);
		values.put(COL_DATE, date);

		return mDb.insert(DB_TABLE, null, values);
	}

	/**
	 * Delete the data with the given rowId
	 * 
	 * @param rowId
	 *            id of data to delete
	 * @return true if deleted, false otherwise
	 */
	public boolean deleteData(long rowId) {
		return mDb.delete(DB_TABLE, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * Delete all the data and recreate the table.
	 * 
	 * @return true if deleted, false otherwise
	 */
	public void recreateTable() {
		mDb.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
		mDb.execSQL(DB_CREATE);
	}

	/**
	 * Return a Cursor over the list of all notes in the database
	 * 
	 * @return Cursor over all notes
	 */
	public Cursor fetchAllData(String filter) {
		return mDb.query(DB_TABLE, new String[] { COL_ID, COL_DATE,
				COL_DATEMOD, COL_COUNT, COL_TOTAL, COL_EDITED, COL_COMMENT },
				filter, null, null, null, SORT_ASCENDING);
	}

	/**
	 * Return a Cursor positioned at the data that matches the given rowId
	 * 
	 * @param rowId
	 *            id of entry to retrieve
	 * @return Cursor positioned to matching entry, if found
	 * @throws SQLException
	 *             if entry could not be found/retrieved
	 */
	public Cursor fetchData(long rowId) throws SQLException {
		Cursor mCursor = mDb.query(true, DB_TABLE, new String[] { COL_ID,
				COL_DATE, COL_DATEMOD, COL_COUNT, COL_TOTAL, COL_EDITED,
				COL_COMMENT }, COL_ID + "=" + rowId, null, null, null, null,
				null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}
		return mCursor;
	}

	/**
	 * Update the data using the details provided. The data to be updated is
	 * specified using the rowId, and it is altered to use the values passed in
	 * 
	 * @param rowId
	 * @param date
	 * @param dateMod
	 * @param edited
	 * @param comment
	 * @return true if the entry was successfully updated, false otherwise
	 */
	public boolean updateData(long rowId, long date, long dateMod, long count,
			long total, boolean edited, String comment) {
		ContentValues values = new ContentValues();
		values.put(COL_DATE, date);
		values.put(COL_DATEMOD, dateMod);
		values.put(COL_COUNT, count);
		values.put(COL_TOTAL, total);
		values.put(COL_EDITED, edited);
		values.put(COL_COMMENT, comment);

		return mDb.update(DB_TABLE, values, COL_ID + "=" + rowId, null) > 0;
	}

	/**
	 * A SQLiteOpenHelper helper to help manage database creation and version
	 * management. Extends a custom version that writes to the SD Card instead
	 * of using the Context.
	 */
	private static class DatabaseHelper extends SDCardSQLiteOpenHelper {
		public DatabaseHelper(String dir) {
			super(dir, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(DB_CREATE);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Re-do this so nothing is lost if there is a need to change
			// the version
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
			onCreate(db);
		}
	}

}
