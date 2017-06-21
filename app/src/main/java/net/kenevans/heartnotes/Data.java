package net.kenevans.heartnotes;

public class Data implements IConstants {
    private String id;
    private String comment;
    private long dateNum = -1;
    private int count;
    private int total;

    public Data(String id, String comment, long dateNum, int count, int total) {
        this.id = id;
        this.comment = comment;
        this.dateNum = dateNum;
        this.count = count;
        this.total = total;
    }

    public String getId() {
        return id;
    }

    public String getComment() {
        return comment;
    }

    public long getDateNum() {
        return dateNum;
    }

    public int getCount() {
        return count;
    }

    public int getTotal() {
        return total;
    }
}
