package com.kidscademy.quiz.instruments.view;

import android.content.Context;
import android.os.Vibrator;
import android.support.v7.widget.GridLayout;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.kidscademy.quiz.instruments.App;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import js.lang.BugError;
import js.util.Player;

public class KeyboardView extends GridLayout implements OnClickListener
{
  public static interface Listener
  {
    /**
     * Handle character generated by keyboard. Event handler implementation should return true if keyboard character is
     * consumed signaling to keyboard to remove it.
     * 
     * @param c character of the currently preset key.
     * @return true if character is consumed by event handler.
     */
    boolean onKeyboardChar(char c);
  }

  private static final List<Character> alphabet = new ArrayList<Character>();
  static {
    String s = "ABCDEFGHIJKLMNOPQRSTUWXYZ";
    for(int i = 0; i < s.length(); ++i) {
      alphabet.add(s.charAt(i));
    }
  }

  private Player player;
  private Listener listener;
  private String expectedName;
  private boolean disabled;

  public KeyboardView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    for(int i = 0; i < getChildCount(); ++i) {
      getChildAt(i).setOnClickListener(this);
    }
  }

  public void init(String expectedName)
  {
    expectedName = expectedName.toUpperCase(Locale.getDefault()).replaceAll("_", "");
    if(expectedName.length() > getChildCount()) {
      throw new BugError("Expected name |%s| too large.", expectedName);
    }
    this.expectedName = expectedName;

    List<Character> keys = new ArrayList<Character>();
    for(int i = 0; i < expectedName.length(); ++i) {
      keys.add(expectedName.charAt(i));
    }

    Collections.shuffle(alphabet);
    keys.addAll(alphabet.subList(0, getChildCount() - expectedName.length()));
    Collections.shuffle(keys);

    for(int i = 0; i < keys.size(); ++i) {
      TextView view = (TextView)getChildAt(i);
      view.setVisibility(View.VISIBLE);
      view.setText(keys.get(i).toString());
    }
  }

  public void setPlayer(Player player)
  {
    this.player = player;
  }

  public void setListener(Listener listener)
  {
    this.listener = listener;
  }

  @Override
  public void onClick(View view)
  {
    if(disabled) {
      return;
    }
    
    player.play("fx/click.mp3");
    if(App.prefs().isKeyVibrator()) {
      Vibrator vibrator = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
      vibrator.vibrate(200);
    }
    
    Character c = ((TextView)view).getText().charAt(0);
    if(listener.onKeyboardChar(c)) {
      view.setVisibility(View.INVISIBLE);
    }
  }

  public char getExpectedChar(int charIndex)
  {
    char c = expectedName.charAt(charIndex);

    for(int i = 0; i < getChildCount(); ++i) {
      TextView view = (TextView)getChildAt(i);
      if(view.getVisibility() == View.VISIBLE && view.getText().charAt(0) == c) {
        view.setVisibility(View.INVISIBLE);
        break;
      }
    }

    return c;
  }

  public void ungetChar(Character c)
  {
    for(int i = 0; i < getChildCount(); ++i) {
      TextView view = (TextView)getChildAt(i);
      if(view.getVisibility() == View.INVISIBLE && view.getText().charAt(0) == c) {
        view.setText(c.toString());
        view.setVisibility(View.VISIBLE);
        break;
      }
    }
  }

  public void hideUnusedLetters()
  {
    List<Character> expectedKeys = new ArrayList<Character>();
    for(int i = 0; i < expectedName.length(); ++i) {
      expectedKeys.add(expectedName.charAt(i));
    }

    for(int i = 0; i < getChildCount(); ++i) {
      TextView view = (TextView)getChildAt(i);
      if(expectedKeys.remove((Character)view.getText().charAt(0))) {
        continue;
      }
      view.setVisibility(View.INVISIBLE);
    }
  }

  public void disable()
  {
    disabled = true;
  }

  public void enable()
  {
    disabled = false;
  }
}