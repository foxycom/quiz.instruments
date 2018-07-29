package com.kidscademy.quiz.instruments.model;

import com.kidscademy.quiz.instruments.util.Audit;
import com.kidscademy.quiz.instruments.util.Storage;

import java.util.List;
import java.util.Locale;

import js.log.Log;
import js.log.LogFactory;

/**
 * Game engine implementation.
 *
 * @author Iulian Rotaru
 */
public class GameEngineImpl implements GameEngine {
    /**
     * Class logger.
     */
    private static final Log log = LogFactory.getLog(GameEngineImpl.class);

    /**
     * Mark value for level not unlocked.
     */
    private static final int NO_UNLOCK_LEVEL = -1;

    /**
     * Application persistent storage.
     */
    private final Storage storage;
    /**
     * Audit utility.
     */
    private final Audit audit;
    /**
     * User balance keeps score points and earned credits.
     */
    private final Balance balance;
    /**
     * Keyboard control.
     */
    private final KeyboardControl keyboard;
    /**
     * Answer builder.
     */
    private final AnswerBuilder answer;
    /**
     * Reference to storage instruments.
     */
    private final Instrument[] instruments;
    /**
     * Engine instance level.
     */
    private Level level;
    /**
     * Associated level state.
     */
    private LevelState levelState;
    /**
     * Instrument currently displayed as challenge.
     */
    private Instrument challengedInstrument;
    /**
     * Current challenged instrument index, relative to level not solved instruments collection.
     */
    private int challengedInstrumentIndex;
    /**
     * If current level unlock threshold was reached this field contains the index of the next level.
     */
    private int unlockedLevelIndex = NO_UNLOCK_LEVEL;

    /**
     * Create game engine instance for requested level.
     *
     * @param storage  persistent storage, global per application,
     * @param audit    audit utility,
     * @param answer   answer builder,
     * @param keyboard keyboard control,
     */
    public GameEngineImpl(Storage storage, Audit audit, AnswerBuilder answer, KeyboardControl keyboard) {
        log.trace("GameEngineImpl(Storage, Audit, AnswerBuilder, KeyboardControl)"); //NON-NLS
        this.storage = storage;
        this.audit = audit;

        this.balance = storage.getBalance();
        this.instruments = storage.getInstruments();

        this.answer = answer;
        this.keyboard = keyboard;
    }

    @Override
    public void setLevelIndex(int levelIndex) {
        level = storage.getLevel(levelIndex);
        levelState = storage.getLevelState(levelIndex);
    }

    @Override
    public void start(String challengeName) {
        log.trace("start(String)"); //NON-NLS
        audit.playGameLevel(level.getIndex());

        List<Integer> unsolvedInstruments = levelState.getUnsolvedInstruments(storage);
        if (unsolvedInstruments.isEmpty()) {
            return;
        }
        if (challengeName == null) {
            challengedInstrument = instruments[unsolvedInstruments.get(0)];
            challengedInstrumentIndex = 1;
        } else {
            for (challengedInstrumentIndex = 0; challengedInstrumentIndex < unsolvedInstruments.size(); ) {
                challengedInstrument = instruments[unsolvedInstruments.get(challengedInstrumentIndex++)];
                if (challengedInstrument.getLocaleName().equals(challengeName)) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean nextChallenge() {
        log.trace("nextChallenge()"); //NON-NLS
        List<Integer> unsolvedInstruments = levelState.getUnsolvedInstruments(storage);
        if (unsolvedInstruments.isEmpty()) {
            audit.gameClose(challengedInstrument);
            balance.plusScore(Balance.getScoreLevelCompleteBonus(levelState.getIndex()));
            challengedInstrument = null;
            return false;
        }
        if (challengedInstrumentIndex >= unsolvedInstruments.size()) {
            challengedInstrumentIndex = 0;
        }
        challengedInstrument = instruments[unsolvedInstruments.get(challengedInstrumentIndex++)];
        return true;
    }

    @Override
    public <T> T getCurrentChallenge() {
        // noinspection unchecked
        return (T) challengedInstrument;
    }

    @Override
    public AnswerState handleAnswerLetter(char letter) {
        if (answer.hasAllLetters()) {
            return AnswerState.OVERFLOW;
        }
        answer.addLetter(letter);
        if (!answer.hasAllLetters()) {
            return AnswerState.FILLING;
        }

        if (!checkAnswer(answer.getValue())) {
            audit.gameWrongAnswer(challengedInstrument, answer.getValue());
            return AnswerState.WRONG;
        }
        audit.gameCorrectAnswer(challengedInstrument);
        return AnswerState.CORRECT;
    }

    /**
     * Helper method, companion of the {@link #handleAnswerLetter(char)} method.
     *
     * @param answer answer value to check.
     * @return true if answer is correct.
     */
    private boolean checkAnswer(String answer) {
        if (!challengedInstrument.getLocaleName().equals(answer.toLowerCase(Locale.getDefault()))) {
            final int penalty = Balance.getScorePenalty();
            balance.minusScore(penalty);
            levelState.minusScore(penalty);
            return false;
        }

        final int points = Balance.getScoreIncrement(levelState.getIndex());
        balance.plusScore(points);
        levelState.plusScore(points);
        levelState.solveInstrument(challengedInstrument.getIndex());
        // decrement brand index to compensate for solved brand that will not be part of unsolved brands on next challenge
        --challengedInstrumentIndex;

        // logic to unlock next level
        // store next level index for unlocking only if next level is not already enabled

        LevelState nextLevel = storage.getNextLevel(levelState.getIndex());
        if (nextLevel != null && !nextLevel.isUnlocked() && levelState.isUnlockThreshold()) {
            nextLevel.unlock();
            unlockedLevelIndex = nextLevel.getIndex();
            final int bonus = Balance.getScoreLevelUnlockBonus(levelState.getIndex());
            balance.plusScore(bonus);
            levelState.plusScore(bonus);
        }
        return true;
    }

    @Override
    public int getLevelIndex() {
        return level.getIndex();
    }

    @Override
    public int getLevelChallengesCount() {
        return level.getInstrumentsCount();
    }

    @Override
    public int getLevelSolvedChallengesCount() {
        return levelState.getSolvedInstrumentsCount();
    }

    @Override
    public void skipChallenge() {
        audit.gameSkip(challengedInstrument);
        nextChallenge();
    }

    @Override
    public boolean wasNextLevelUnlocked() {
        return unlockedLevelIndex != NO_UNLOCK_LEVEL;
    }

    /**
     * Get unlocked level index. This getter returns meaningful value only if {@link #wasNextLevelUnlocked()} return true;
     * otherwise behavior is not specified. Returned value is usable only once, that is, this method reset internal value
     * before returning.
     *
     * @return unlocked level index.
     */
    public int getUnlockedLevelIndex() {
        int i = unlockedLevelIndex;
        unlockedLevelIndex = NO_UNLOCK_LEVEL;
        return i;
    }

    @Override
    public boolean revealLetter() {
        if (!balance.deductRevealLetter()) {
            return false;
        }
        audit.gameHint(challengedInstrument, "REVEAL_LETTER"); //NON-NLS
        int firstMissingCharIndex = answer.getFirstMissingLetterIndex();
        handleAnswerLetter(keyboard.getExpectedChar(firstMissingCharIndex));
        return true;
    }

    @Override
    public boolean isInputVerifyAllowed() {
        if (balance.deductVerifyInput()) {
            audit.gameHint(challengedInstrument, "VERIFY_INPUT"); //NON-NLS
            return true;
        }
        return false;
    }

    @Override
    public boolean hideLetters() {
        if (balance.deductHideLettersInput()) {
            audit.gameHint(challengedInstrument, "HIDE_LETTERS"); //NON-NLS
            keyboard.hideUnusedLetters();
            return true;
        }
        return false;
    }

    @Override
    public boolean playSample() {
        return balance.deductSayName();
    }

    @Override
    public boolean hasCredit() {
        return balance.hasCredit();
    }

    @Override
    public int getScore() {
        return balance.getScore();
    }

    @Override
    public int getCredit() {
        return balance.getCredit();
    }
}
