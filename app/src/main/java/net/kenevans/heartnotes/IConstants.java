//Copyright (c) 2011 Kenneth Evans
//
//Permission is hereby granted, free of charge, to any person obtaining
//a copy of this software and associated documentation files (the
//"Software"), to deal in the Software without restriction, including
//without limitation the rights to use, copy, modify, merge, publish,
//distribute, sublicense, and/or sell copies of the Software, and to
//permit persons to whom the Software is furnished to do so, subject to
//the following conditions:
//
//The above copyright notice and this permission notice shall be included
//in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
//EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
//MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
//IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
//CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
//TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.kenevans.heartnotes;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Holds constant values used by several classes in the application.
 */
public interface IConstants {
    // Log tag
    /**
     * Tag to associate with log messages.
     */
    String TAG = "HeartNotes";

    /**
     * Directory on the SD card where the database is stored
     */
    String SD_CARD_DB_DIRECTORY = "Heart Notes";

    /**
     * Simple name of the database.
     */
    String DB_NAME = "HeartNotes.db";    /**
    /**
     * Simple name of the table.
     */
    String DB_TABLE = "data";
    /**
     * The database version
     */
    int DB_VERSION = 1;

    // Preferences
    String PREF_DATA_DIRECTORY = "dataDirectory";

    // Information
    /**
     * Key for information URL sent to InfoActivity.
     */
    String INFO_URL = "InformationURL";

    // Database
    /**
     * Database column for the id. Identifies the row.
     */
    String COL_ID = "_id";
    /**
     * Database column for the count.
     */
    String COL_COUNT = "count";
    /**
     * Database column for the total.
     */
    String COL_TOTAL = "total";
    /**
     * Database column for the comment.
     */
    String COL_COMMENT = "comment";
    /**
     * Database column for the date.
     */
    String COL_DATE = "date";
    /**
     * Database column for the modification date.
     */
    String COL_DATEMOD = "datemod";
    /**
     * Database column for edited.
     */
    String COL_EDITED = "edited";

    /**
     * SQL sort command for date ascending
     */
    String SORT_ASCENDING = COL_DATE + " ASC";

    // Messages
    /**
     * Request code for creating new data.
     */
    int ACTIVITY_CREATE = 0;
    /**
     * Request code for editing data.
     */
    int ACTIVITY_EDIT = 1;

    /**
     * The static long formatter to use for formatting dates.
     */
    SimpleDateFormat longFormatter = new SimpleDateFormat(
            "MMM dd, yyyy HH:mm:ss Z", Locale.US);
//	SimpleDateFormat longFormatter = new SimpleDateFormat(
//			"hh:mm a MMM dd, yyyy", Locale.US);

    /**
     * The static formatter to use for formatting dates.
     */
    SimpleDateFormat mediumFormatter = new SimpleDateFormat(
            "MMM dd, yyyy HH:mm:ss", Locale.US);

    /**
     * The static short formatter to use for formatting dates.
     */
    SimpleDateFormat shortFormatter = new SimpleDateFormat(
            "M/d/yy h:mm a", Locale.US);

}
