/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.menny.android.anysoftkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

import com.menny.android.anysoftkeyboard.R;
import com.menny.android.anysoftkeyboard.keyboards.AnyKeyboard;
import com.menny.android.anysoftkeyboard.keyboards.EnglishKeyboard;
import com.menny.android.anysoftkeyboard.keyboards.GenericKeyboard;
import com.menny.android.anysoftkeyboard.keyboards.HebrewKeyboard;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService 
        implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;
    
    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    //static final boolean PROCESS_HARD_KEYS = true;
    
    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;
    
    private StringBuilder mComposing = new StringBuilder();
    //private boolean mPredictionOn;
    private boolean mCompletionOn;
    
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    //private long mMetaState;
    
    private AnyKeyboard mSymbolsKeyboard;
    private AnyKeyboard mSymbolsShiftedKeyboard;
    private AnyKeyboard mInternetKeyboard;
    private AnyKeyboard mSimpleNumbersKeyboard;
    //my working keyboards
    private AnyKeyboard[] mKeyboards = null;
    private int mLastSelectedKeyboard = 0;
    
    private AnyKeyboard mCurKeyboard;
    
    private String mWordSeparators;
    
    private boolean mVibrateOnKeyPress = false;
    private boolean mSoundOnKeyPress = false;
    private boolean mAutoCaps = false;
    private boolean mShowCandidates = false;
    
    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);
    }
    
    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        if (mKeyboards != null) 
        {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        
        mSymbolsKeyboard = new GenericKeyboard(this, R.xml.symbols, false, "Symbols");
        mSymbolsShiftedKeyboard = new GenericKeyboard(this, R.xml.symbols_shift, false, "Shift Symbols");
        mInternetKeyboard = new GenericKeyboard(this, R.xml.internet_qwerty, false, "Internet");
        mSimpleNumbersKeyboard = new GenericKeyboard(this, R.xml.simple_numbers, false, "Numbers");
        
        mKeyboards = new AnyKeyboard[2];
        mKeyboards[0] = new EnglishKeyboard(this);
        mKeyboards[1] = new HebrewKeyboard(this);
        
        reloadConfiguration();
    }

    private void reloadConfiguration()
    {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        mVibrateOnKeyPress = sp.getBoolean("vibrate_on", false);
        mSoundOnKeyPress = sp.getBoolean("sound_on", false);
        
        if (mSoundOnKeyPress)
        	((AudioManager)getSystemService(Context.AUDIO_SERVICE)).loadSoundEffects();
        
        mAutoCaps = sp.getBoolean("auto_caps", true);
        mShowCandidates = sp.getBoolean("candidates_on", true);
    	setEnabledKeyboards();
    }
    
	private void setEnabledKeyboards() 
	{
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean englishKeyboard = sp.getBoolean("eng_keyboard", true);
        boolean hebrewKeyboard = sp.getBoolean("heb_keyboard", true);
        boolean internetKeyboard = sp.getBoolean("internet_keyboard", false);
        if ((!hebrewKeyboard))
        	englishKeyboard = true;
        
        mInternetKeyboard.setIsEnabled(internetKeyboard);
        mKeyboards[0].setIsEnabled(englishKeyboard);
        mKeyboards[1].setIsEnabled(hebrewKeyboard);
        
        int maxTries = mKeyboards.length;
		do
		{
			if (mLastSelectedKeyboard >= mKeyboards.length)
				mLastSelectedKeyboard = 0;
			if (mKeyboards[mLastSelectedKeyboard].isEnabled())
				break;
			maxTries--;
			mLastSelectedKeyboard++;
		}while(maxTries > 0);
	}
    
    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override public View onCreateInputView() {
        mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        reloadConfiguration();
        mInputView.setKeyboard(mKeyboards[mLastSelectedKeyboard]);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() 
    {
    	if (mCompletionOn)
    	{
    		mCandidateView = new CandidateView(this);
    		mCandidateView.setService(this);
    		return mCandidateView;
    	}
    	else
    		return null;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        
        reloadConfiguration();
        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();
        
        if (!restarting) 
        {
            // Clear shift states.
            //mMetaState = 0;
        }
        
        mCompletions = null;
        
        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                //mCurKeyboard = mSymbolsKeyboard;
            	mCurKeyboard = mSimpleNumbersKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                //mCurKeyboard = mSymbolsKeyboard;
            	mCurKeyboard = mSimpleNumbersKeyboard;
                break;
                
            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mKeyboards[mLastSelectedKeyboard];
                //mPredictionOn = mShowCandidates;
                mCompletionOn = mShowCandidates;
                
                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    //mPredictionOn = false;
                	mCompletionOn = false;
                }
                
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    //mPredictionOn = false;
                	mCompletionOn = false;
                }
                
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI) {
                    //special keyboard
                	if (mInternetKeyboard.isEnabled())
                		mCurKeyboard = mInternetKeyboard;
                	else
                		mCurKeyboard = mKeyboards[mLastSelectedKeyboard];
                }
                
                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    //mPredictionOn = false;
                    mCompletionOn = isFullscreenMode() && mShowCandidates;
                }
                
                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;
                
            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mKeyboards[mLastSelectedKeyboard];
                updateShiftKeyState(attribute);
        }
        
        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();
        
        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();
        
        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);
        
        mCurKeyboard = mKeyboards[mLastSelectedKeyboard];
        if (mInputView != null) {
            mInputView.closing();
        }
        
        if (mSoundOnKeyPress)
        	((AudioManager)getSystemService(Context.AUDIO_SERVICE)).unloadSoundEffects();
    }
    
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
       
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
    }
    
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
            int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        
        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) 
        {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }
            
            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }
    
    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) 
    {
        if (attr != null && mInputView != null) 
        {
            int caps = 0;
            //EditorInfo ei = getCurrentInputEditorInfo();
            //if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) 
            //{
            if (mAutoCaps)
            {
            	InputConnection ci = getCurrentInputConnection();
            	if (ci != null)
            		caps = ci.getCursorCapsMode(attr.inputType);
            }
            //}
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }
    
    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) 
    {
        return mCurKeyboard.isLetter((char)code);
    }
    
    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }
    
    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener

    public void onKey(int primaryCode, int[] keyCodes) {
    	EditorInfo currentEditorInfo = getCurrentInputEditorInfo();
    	InputConnection currentInputConnection = getCurrentInputConnection();
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(currentInputConnection);
            }
            sendKey(primaryCode);
            updateShiftKeyState(currentEditorInfo);
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == AnyKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        }
        else if (primaryCode == -80//my special .COM key
                && mInputView != null) {
        	commitTyped(currentInputConnection);
        	currentInputConnection.commitText(".com", 4);
        } else if (primaryCode == -99//my special lang key
                && mInputView != null) {
        	int variation = currentEditorInfo.inputType &  EditorInfo.TYPE_MASK_VARIATION;
            if (mInternetKeyboard.isEnabled() &&
            		(variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_URI)) {
                //special keyboard
           		mCurKeyboard = mInternetKeyboard;
            }            
            else
            {
	        	if (isAlphaBetKeyboard(mInputView.getKeyboard()))
	        	{
	        		int maxTries = mKeyboards.length;
	        		do
	        		{
	        			mLastSelectedKeyboard++;
	        			if (mLastSelectedKeyboard >= mKeyboards.length)
	        				mLastSelectedKeyboard = 0;
	        			if (mKeyboards[mLastSelectedKeyboard].isEnabled())
	        				break;
	        			maxTries--;
	        		}while(maxTries > 0);
	        	}
	        	mCurKeyboard = mKeyboards[mLastSelectedKeyboard];
            }
        	mInputView.setKeyboard(mCurKeyboard);
        	updateShiftKeyState(currentEditorInfo);
        	mCurKeyboard.setImeOptions(getResources(), currentEditorInfo.imeOptions);
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }
    
    private boolean isAlphaBetKeyboard(Keyboard viewedKeyboard)
    {
    	for(int i=0; i<mKeyboards.length; i++)
    	{
    		if (viewedKeyboard == mKeyboards[i])
    			return true;
    	}
    	
    	return false;
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() 
    {
        if (mCompletionOn) 
        {
            if (mComposing.length() > 0) 
            {
                ArrayList<String> list = new ArrayList<String>();
                String currentWord = mComposing.toString();
                list.add(currentWord);
                //asking current keyboard for suggestions
                mCurKeyboard.addSuggestions(currentWord, list);
                setSuggestions(list, true, true);
            } 
            else 
            {
                setSuggestions(null, false, false);
            }
        }
    }
    
    public void setSuggestions(List<String> suggestions, boolean completions,
            boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null /*&& mPredictionOn*/) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }
    
    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }
        
        AnyKeyboard currentKeyboard = (AnyKeyboard)mInputView.getKeyboard();
        if (currentKeyboard.getSupportsShift()) {
            // Alphabet with shift support keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }
    
    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) 
        {
            if (mInputView.isShifted() && mCurKeyboard.getSupportsShift()) 
            {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode)) 
        {
            mComposing.append((char) primaryCode);
            
            if (mCompletionOn)
            	getCurrentInputConnection().setComposingText(mComposing, 1);
            else
            	getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
            
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } 
        else 
        {
            getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }
    
    private String getWordSeparators() {
        return mWordSeparators;
    }
    
    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }

//    public void pickDefaultCandidate() 
//    {
//        pickSuggestionManually(0);
//    }
    
    public void pickSuggestionManually(String word) 
    {
//        if (mCompletionOn && mCompletions != null && index >= 0
//                && index < mCompletions.length) {
//            CompletionInfo ci = mCompletions[index];
//            getCurrentInputConnection().commitCompletion(ci);
//            if (mCandidateView != null) {
//                mCandidateView.clear();
//            }
//            updateShiftKeyState(getCurrentInputEditorInfo());
//        } else if (mComposing.length() > 0) {
//            // If we were generating candidate suggestions for the current
//            // text, we would commit one of them here.  But for this sample,
//            // we will just commit the current text.
//            commitTyped(getCurrentInputConnection());
//        }
    	getCurrentInputConnection().commitText(word, word.length());
		mComposing.setLength(0);
		//simulating space
		onKey((int)' ', null);
		if (mCandidateView != null) 
		{
		      mCandidateView.clear();
		}
    }
    
    public void swipeRight() 
    {
    }
    
    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }
    
    public void onPress(int primaryCode) {
    	if(mVibrateOnKeyPress)
    	{
    		((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(12);
    	}
    	if(mSoundOnKeyPress)
    	{
    		int keyFX = AudioManager.FX_KEY_CLICK;
    		switch(primaryCode)
    		{
    			case 13:
    				keyFX = AudioManager.FX_KEYPRESS_RETURN;
    			case Keyboard.KEYCODE_DELETE:
    				keyFX = AudioManager.FX_KEYPRESS_DELETE;
    			case 32:
    				keyFX = AudioManager.FX_KEYPRESS_SPACEBAR;
    		}
    		((AudioManager)getSystemService(Context.AUDIO_SERVICE)).playSoundEffect(keyFX);
    	}
    }    

	public void onRelease(int primaryCode) {
    }
}