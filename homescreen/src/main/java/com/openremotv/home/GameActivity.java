package com.openremotv.home;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two small games for the remote, opened from the Today page: Memory (find the eight pairs) and Tic-tac-toe
 * against the TV. Every square is a focusable view, so the arrows move between them and OK plays. On MangaHome
 * the board is drawn as a comic page: screentone paper, ink-lined squares, a red caption box on the focused one.
 */
public class GameActivity extends Activity {

    static final String EXTRA_GAME = "game";
    private static final String[] PICTURES = {"🐘", "🌸", "🏏", "🍵", "🌙", "⭐", "🐯", "🌻"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private float density;
    private TextView status;
    private TextView[] cells;
    // memory
    private String[] faces;
    private boolean[] matched;
    private int first = -1, moves, pairs;
    private boolean busy;
    // tic-tac-toe
    private char[] board;
    private boolean over;
    private boolean comic;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        L10n.init(this);
        density = getResources().getDisplayMetrics().density;
        try {
            Theme theme = Theme.load(this);
            comic = theme.mangaLayout || theme.mangaStyle;
        } catch (RuntimeException e) {
            comic = false;
        }
        int ink = comic ? MangaSkin.INK : 0xFFFFFFFF;
        FrameLayout root = new FrameLayout(this);
        if (comic) root.setBackground(new ComicHome.PageDrawable(density));
        else root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A1430, 0xFF0B1020, 0xFF02040A}));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(0, dp(28), 0, dp(20));
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        status.setTextColor(ink);
        status.setGravity(Gravity.CENTER);
        if (comic) {
            // The status as a speech bubble in comic lettering.
            status.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.ITALIC));
            status.setBackground(MangaSkin.bubble(density));
            status.setPadding(dp(28), 0, dp(32), dp(4));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(60));
            sp.gravity = Gravity.CENTER_HORIZONTAL;
            sp.bottomMargin = dp(10);
            column.addView(status, sp);
        } else {
            column.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        }
        boolean memory = !"tictactoe".equals(getIntent().getStringExtra(EXTRA_GAME));
        int size = memory ? 4 : 3;
        int cell = memory ? 88 : 120;
        cells = new TextView[size * size];
        for (int r = 0; r < size; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            // Centred on the screen: a full-width row would leave the board at the left edge.
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            column.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < size; c++) {
                int i = r * size + c;
                TextView t = new TextView(this);
                t.setGravity(Gravity.CENTER);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, memory ? 40 : 56);
                t.setTextColor(ink);
                t.setFocusable(true);
                t.setClickable(true);
                t.setBackground(comic ? comicSquare() : glassSquare());
                t.setOnClickListener(v -> {
                    if (memory) flip(i);
                    else play(i);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(cell), dp(cell));
                lp.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(t, lp);
                cells[i] = t;
            }
        }
        TextView hint = new TextView(this);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setTextColor(comic ? 0xB3141414 : 0x99FFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setText(R.string.game_hint);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(12);
        column.addView(hint, hp);
        setContentView(root);
        if (memory) newMemory();
        else newTicTacToe();
        cells[0].requestFocus();
    }

    // ---- memory ----------------------------------------------------------------------------------------

    private void newMemory() {
        List<String> deck = new ArrayList<>();
        for (String p : PICTURES) {
            deck.add(p);
            deck.add(p);
        }
        Collections.shuffle(deck);
        faces = deck.toArray(new String[0]);
        matched = new boolean[faces.length];
        first = -1;
        moves = 0;
        pairs = 0;
        for (TextView t : cells) t.setText("");
        status.setText(R.string.game_memory_start);
    }

    private void flip(int i) {
        if (pairs == PICTURES.length) {
            newMemory();
            return;
        }
        if (busy || matched[i] || i == first) return;
        cells[i].setText(faces[i]);
        if (first < 0) {
            first = i;
            return;
        }
        moves++;
        int a = first;
        first = -1;
        if (faces[a].equals(faces[i])) {
            matched[a] = matched[i] = true;
            pairs++;
            status.setText(pairs == PICTURES.length
                    ? getString(R.string.game_memory_won, moves)
                    : getString(R.string.game_memory_pair, pairs, PICTURES.length));
        } else {
            busy = true;
            status.setText(getString(R.string.game_moves, moves));
            handler.postDelayed(() -> {
                cells[a].setText("");
                cells[i].setText("");
                busy = false;
            }, 900);
        }
    }

    // ---- tic-tac-toe -----------------------------------------------------------------------------------

    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private void newTicTacToe() {
        board = "         ".toCharArray();
        over = false;
        for (TextView t : cells) t.setText("");
        status.setText(R.string.game_ttt_start);
    }

    private void play(int i) {
        if (over) {
            newTicTacToe();
            return;
        }
        if (busy || board[i] != ' ') return;
        board[i] = 'X';
        cells[i].setText("✕");
        if (comic) cells[i].setTextColor(MangaSkin.RED);
        if (finished()) return;
        busy = true;
        status.setText(R.string.game_ttt_thinking);
        handler.postDelayed(() -> {
            int move = best();
            board[move] = 'O';
            cells[move].setText("○");
            if (comic) cells[move].setTextColor(MangaSkin.INK);
            busy = false;
            if (!finished()) status.setText(R.string.game_ttt_your_turn);
        }, 500);
    }

    private boolean finished() {
        char w = winner(board);
        boolean full = new String(board).indexOf(' ') < 0;
        if (w == ' ' && !full) return false;
        over = true;
        status.setText(getString(w == 'X' ? R.string.game_ttt_win : w == 'O' ? R.string.game_ttt_lose : R.string.game_ttt_draw));
        return true;
    }

    private static char winner(char[] b) {
        for (int[] l : LINES) if (b[l[0]] != ' ' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        return ' ';
    }

    /** The TV's move: minimax, with a random first reply so games differ. */
    private int best() {
        int empty = 0;
        for (char c : board) if (c == ' ') empty++;
        if (empty == 8) {
            if (board[4] == ' ') return 4;
            int[] corners = {0, 2, 6, 8};
            return corners[new java.util.Random().nextInt(4)];
        }
        java.util.Random random = new java.util.Random();
        if (random.nextInt(5) == 0) {   // now and then a careless move, so the TV can be beaten
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < 9; i++) if (board[i] == ' ') free.add(i);
            return free.get(random.nextInt(free.size()));
        }
        int bestMove = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i] != ' ') continue;
            board[i] = 'O';
            int score = minimax(false);
            board[i] = ' ';
            if (score > bestScore) {
                bestScore = score;
                bestMove = i;
            }
        }
        return bestMove;
    }

    private int minimax(boolean tvTurn) {
        char w = winner(board);
        if (w == 'O') return 1;
        if (w == 'X') return -1;
        if (new String(board).indexOf(' ') < 0) return 0;
        int best = tvTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i] != ' ') continue;
            board[i] = tvTurn ? 'O' : 'X';
            int s = minimax(!tvTurn);
            board[i] = ' ';
            best = tvTurn ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }

    private StateListDrawable glassSquare() {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, square(0x59FFFFFF, 0xFFFFFFFF, 3));
        bg.addState(new int[]{}, square(0x33FFFFFF, 0x33FFFFFF, 1));
        return bg;
    }

    /** A comic square: paper in an ink line with a hard ink shadow; focused, a yellow caption box in a red line. */
    private StateListDrawable comicSquare() {
        float r = dp(10);
        int drop = dp(5);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, dropped(
                MangaSkin.shape(0xFFFFF4C2, dp(5), MangaSkin.RED, r), MangaSkin.shape(MangaSkin.INK, 0, 0, r), drop));
        bg.addState(new int[]{}, dropped(
                MangaSkin.shape(MangaSkin.PAPER, dp(3), MangaSkin.INK, r), MangaSkin.shape(MangaSkin.INK, 0, 0, r), drop));
        return bg;
    }

    private static android.graphics.drawable.Drawable dropped(android.graphics.drawable.Drawable front,
                                                               android.graphics.drawable.Drawable shadow, int drop) {
        android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{shadow, front});
        l.setLayerInset(0, drop, drop, 0, 0);
        l.setLayerInset(1, 0, 0, drop, drop);
        return l;
    }

    private GradientDrawable square(int fill, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(strokeDp), stroke);
        d.setCornerRadius(dp(16));
        return d;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
