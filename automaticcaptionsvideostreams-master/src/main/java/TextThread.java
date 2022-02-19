import javafx.scene.control.TextField;

import java.util.ArrayList;

public class TextThread implements Runnable{
    private TextField text;
    TextThread(TextField text) {
        this.text = text;
    }
    @Override
    public void run() {
        VoskClient client = new VoskClient();
        try {
            ArrayList<String> subtitles = client.transcribe(text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
