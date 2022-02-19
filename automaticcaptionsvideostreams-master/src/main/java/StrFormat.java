import java.text.DecimalFormat;

public class StrFormat {
    private int id;
    private String time;
    private String subtitle;
    private int h1, h2;
    private int min1, min2;
    private double sec1, sec2;
    private double increment;
    //private DecimalFormat decimalFormat = new DecimalFormat("#0.000");

    StrFormat() {
        this.id = 1;
        this.h1 = 0;
        this.h2 = 0;
        this.min1 = 0;
        this.min2 = 0;
        this.sec1 = 0;
        this.sec2 = 1;
        //00:00:03,000 --> 00:00:05,000
        this.subtitle = "";
        this.time = "";
    }
    public void setSubtitle(String s) {
        this.subtitle = s.substring(17);
    }
    public void setTime() {
        String s1 = String.format("%02d", h1);
        String s2 = String.format("%02d", h2);
        String s3 = String.format("%02d", min1);
        String s4 = String.format("%02d", min2);
        String s5 = String.format("%06.3f", sec1);
        String s6 = String.format("%06.3f", sec2);
        this.time = s1 + ":" + s3 + ":" + s5 + " --> " + s2 + ":" + s4 + ":" + s6;
        sec1+=increment;
        sec2+=increment;
        if(sec1 >= 60) {
            min1++;
            sec1 = sec1-60;
        }
        if(sec2 >= 60) {
            min2 ++;
            sec2 = sec2-60;
        }
    }
    public void setIncrement(long duration, int size) {
        this.increment = (double)duration/(double)size;
    }
    public void incrementId() {
        this.id ++;
    }

    @Override
    public String toString() {
        return id + "\n" + time + "\n" + subtitle + "\n\n";
    }
}
