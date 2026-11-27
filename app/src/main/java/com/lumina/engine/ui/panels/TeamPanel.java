package com.lumina.engine.ui.panels;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.lumina.engine.R;
import com.lumina.engine.team.TeamClient;
import com.lumina.engine.team.TeamSession;
import com.lumina.engine.team.TeamSettings;
import com.lumina.engine.ui.AvatarView;
import com.lumina.engine.ui.Bridge;
import com.lumina.engine.ui.Dialogs;
import com.lumina.engine.ui.EditorShell;
import com.lumina.engine.ui.PanelHost;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeamPanel implements PanelHost.Panel, TeamSession.Listener {
    private static final int MIC_REQUEST = 41;
    private static final int NOTIFY_REQUEST = 42;
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout content;
    private LinearLayout chatBox;
    private EditText chatInput;
    private String chatDraft = "";
    private String projectPath;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Map<String, AvatarView> voiceAvatars = new HashMap<>();
    private final Map<String, Boolean> voiceSpeaking = new HashMap<>();
    private final Runnable voiceTick = new Runnable() {
        @Override public void run() {
            Context c = shell.activity();
            if (c == null || root == null) return;
            TeamSession s = TeamSession.get();
            for (Map.Entry<String, AvatarView> entry : voiceAvatars.entrySet()) {
                boolean speaking = s.isSpeaking(entry.getKey());
                Boolean old = voiceSpeaking.get(entry.getKey());
                if (old == null || old != speaking) {
                    voiceSpeaking.put(entry.getKey(), speaking);
                    entry.getValue().setSpeaking(speaking);
                }
            }
            if (s.inVoice()) root.postDelayed(this, 220);
        }
    };

    public TeamPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("team_title", "Colaborar"); }
    @Override public int icon() { return R.drawable.ic_lumina_group; }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        TeamSession.get().init(c);
        TeamSession.get().addListener(this);
        root = new ScrollView(c);
        content = Ui.column(c);
        content.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 24));
        root.addView(content);
        loadProject(c);
        return root;
    }

    @Override
    public void onShown() {
        Context c = shell.activity();
        TeamSession.get().init(c);
        TeamSession.get().addListener(this);
        loadProject(c);
        render(c);
    }

    public void dispose() {
        if (root != null) root.removeCallbacks(voiceTick);
        TeamSession.get().removeListener(this);
    }

    @Override public void onHidden() {
        if (root != null) root.removeCallbacks(voiceTick);
    }

    private void loadProject(Context c) {
        Bridge.query(() -> Bridge.editor() == null ? null : Bridge.editor().getProject().getPath(), path -> {
            if (path == null) {
                render(c);
                return;
            }
            if (!path.equals(projectPath)) {
                projectPath = path;
                chatDraft = "";
                TeamSession.get().load(c, path, () -> render(c));
            } else {
                render(c);
            }
        });
    }

    private void render(Context c) {
        if (content == null || c == null) return;
        if (chatInput != null) chatDraft = chatInput.getText().toString();
        if (root != null) root.removeCallbacks(voiceTick);
        voiceAvatars.clear();
        voiceSpeaking.clear();
        int scrollY = root == null ? 0 : root.getScrollY();
        boolean follow = true;
        if (root != null && root.getChildAt(0) != null) {
            follow = scrollY + root.getHeight() >= root.getChildAt(0).getHeight() - Ui.dp(c, 24);
        }
        content.removeAllViews();
        TeamSession s = TeamSession.get();
        TeamSettings.Membership m = s.membership();

        LinearLayout head = card(c, content);
        LinearLayout top = Ui.row(c);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(s.isConnected() ? Ui.SUCCESS : ("offline".equals(s.state()) ? Ui.WARNING : Ui.TEXT_3), Ui.dp(c, 5)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
        dlp.setMarginEnd(Ui.dp(c, 8));
        top.addView(dot, dlp);
        TextView state = Ui.text(c, stateText(s), 15, Ui.TEXT);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(state, Ui.weight(1));
        if (m != null) {
            TextView role = Ui.text(c, roleName(m.role), 12, Ui.ACCENT);
            top.addView(role);
        }
        head.addView(top, Ui.matchWrap());
        TextView hint = Ui.text(c, Tr.t("team_hint", "Crea un equipo para editar el proyecto entre varios en tiempo real."), 12.5f, Ui.TEXT_2);
        hint.setPadding(0, Ui.dp(c, 6), 0, 0);
        head.addView(hint, Ui.matchWrap());

        if (m == null) {
            head.addView(Ui.button(c, R.drawable.ic_lumina_group, Tr.t("team_create", "Crear equipo"), true, v -> createDialog(c)), Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 0));
            head.addView(Ui.button(c, R.drawable.ic_lumina_link, Tr.t("team_join", "Unirme con una invitación"), false, v -> joinDialog(c)), Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
            return;
        }

        LinearLayout room = card(c, content);
        TextView roomName = Ui.text(c, m.roomName == null || m.roomName.isEmpty() ? Tr.t("team_room", "Equipo") : m.roomName, 16, Ui.TEXT);
        roomName.setTypeface(Typeface.DEFAULT_BOLD);
        room.addView(roomName, Ui.matchWrap());
        TextView id = Ui.text(c, "ID: " + shortId(m.roomId), 12, Ui.TEXT_3);
        id.setTypeface(Typeface.MONOSPACE);
        id.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 6));
        room.addView(id, Ui.matchWrap());

        if (m.canManage() && m.inviteCode != null && !m.inviteCode.isEmpty()) {
            TextView invite = Ui.text(c, m.inviteText(), 12, Ui.TEXT_2);
            invite.setTypeface(Typeface.MONOSPACE);
            invite.setTextIsSelectable(true);
            invite.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
            room.addView(invite, Ui.matchWrap());
            LinearLayout actions = Ui.row(c);
            actions.addView(Ui.button(c, R.drawable.ic_lumina_copy, Tr.t("team_copy_invite", "Copiar"), false, v -> Dialogs.copy(c, m.inviteText())));
            LinearLayout email = Ui.button(c, R.drawable.ic_lumina_send, Tr.t("team_email_invite", "Invitar por correo"), true, v -> shareInvite(c, m));
            ((LinearLayout.LayoutParams) email.getLayoutParams()).setMarginStart(Ui.dp(c, 8));
            actions.addView(email);
            room.addView(actions, Ui.matchWrap());
            TextView inviteHint = Ui.text(c, Tr.t("team_invite_hint", "Comparte ese código. La otra persona lo pega en Colaborar → Unirme."), 11.5f, Ui.TEXT_3);
            inviteHint.setPadding(0, Ui.dp(c, 6), 0, 0);
            room.addView(inviteHint, Ui.matchWrap());
        }

        LinearLayout tools = Ui.row(c);
        if (m.canEdit() && projectPath != null) {
            tools.addView(Ui.button(c, R.drawable.ic_lumina_upload, Tr.t("team_publish", "Publicar proyecto"), true, v -> publish(c)));
        }
        LinearLayout reconnect = Ui.button(c, R.drawable.ic_lumina_refresh, Tr.t("team_reconnect", "Reconectar"), false, v -> s.reconnect());
        ((LinearLayout.LayoutParams) reconnect.getLayoutParams()).setMarginStart(Ui.dp(c, 8));
        tools.addView(reconnect);
        LinearLayout leave = Ui.button(c, R.drawable.ic_lumina_close, Tr.t("team_leave", "Salir"), false, v ->
                Dialogs.confirm(c, Tr.t("team_leave", "Salir"), Tr.t("team_leave_q", "Dejarás de ver este equipo en este proyecto."), null, () -> {
                    chatDraft = "";
                    s.leave();
                    render(c);
                }));
        ((LinearLayout.LayoutParams) leave.getLayoutParams()).setMarginStart(Ui.dp(c, 8));
        tools.addView(leave);
        room.addView(tools, Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 0));

        content.addView(Ui.sectionLabel(c, Tr.t("team_members", "Miembros")));
        LinearLayout members = card(c, content);
        List<TeamClient.Member> list = s.members();
        if (list.isEmpty()) {
            members.addView(Ui.text(c, Tr.t("team_no_members", "Conectando…"), 13, Ui.TEXT_3), Ui.matchWrap());
        } else {
            for (TeamClient.Member member : list) {
                members.addView(memberRow(c, member, m, s), Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 4));
            }
        }

        content.addView(Ui.sectionLabel(c, Tr.t("team_now", "Ahora en el proyecto")));
        LinearLayout nowCard = card(c, content);
        boolean anybody = false;
        for (TeamClient.Member member : list) {
            TeamClient.Presence p = member == null ? null : s.presenceFor(member.id);
            if (p == null || (!"scene".equals(p.area) && !"code".equals(p.area))) continue;
            if (p.scene == null || p.scene.isEmpty()) continue;
            if ("code".equals(p.area) && (p.file == null || p.file.isEmpty())) continue;
            nowCard.addView(presenceRow(c, member, p, s), Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 4));
            anybody = true;
        }
        if (!anybody) {
            nowCard.addView(Ui.text(c, Tr.t("team_now_none", "Nadie está en una escena o archivo ahora."), 12.5f, Ui.TEXT_3), Ui.matchWrap());
        }

        content.addView(Ui.sectionLabel(c, Tr.t("team_voice", "Voz")));
        LinearLayout voiceCard = card(c, content);
        List<String> inCall = s.voiceMembers();
        boolean talking = s.inVoice();
        String callState;
        if (talking) {
            callState = s.voiceMuted()
                    ? Tr.t("team_voice_muted", "Micrófono silenciado")
                    : Tr.t("team_voice_on", "Estás en la llamada");
        } else if (inCall.isEmpty()) {
            callState = Tr.t("team_voice_empty", "Nadie está en la llamada");
        } else {
            callState = String.format(Locale.getDefault(), Tr.t("team_voice_count", "%d en llamada"), inCall.size());
        }
        TextView voiceHint = Ui.text(c, callState, 12.5f, talking ? Ui.SUCCESS : Ui.TEXT_2);
        voiceHint.setPadding(0, 0, 0, Ui.dp(c, 8));
        voiceCard.addView(voiceHint, Ui.matchWrap());

        LinkedHashSet<String> callIds = new LinkedHashSet<>();
        if (talking && m.memberId != null) callIds.add(m.memberId);
        callIds.addAll(inCall);
        if (!callIds.isEmpty()) {
            List<String> ids = new ArrayList<>(callIds);
            LinearLayout grid = Ui.column(c);
            int avatarDp = ids.size() == 1 ? 68 : 54;
            for (int i = 0; i < ids.size(); i += 2) {
                LinearLayout row = Ui.row(c);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i + 1 < ids.size()) left.setMarginEnd(Ui.dp(c, 6));
                row.addView(voiceTile(c, ids.get(i), s, avatarDp), left);
                if (i + 1 < ids.size()) {
                    LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    right.setMarginStart(Ui.dp(c, 6));
                    row.addView(voiceTile(c, ids.get(i + 1), s, avatarDp), right);
                } else {
                    View spacer = new View(c);
                    row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
                }
                grid.addView(row, Ui.margins(Ui.matchWrap(), c, 0, i == 0 ? 2 : 10, 0, 0));
            }
            voiceCard.addView(grid, Ui.matchWrap());
        }

        LinearLayout voiceButtons = Ui.row(c);
        if (talking) {
            LinearLayout mute = Ui.button(c, R.drawable.ic_lumina_sound,
                    s.voiceMuted() ? Tr.t("team_voice_unmute", "Activar micrófono") : Tr.t("team_voice_mute", "Silenciar"),
                    false, v -> {
                        s.toggleVoiceMute();
                        render(c);
                    });
            voiceButtons.addView(mute, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f));
            LinearLayout leaveCall = Ui.button(c, R.drawable.ic_lumina_close,
                    Tr.t("team_voice_leave", "Salir de la llamada"), true, v -> {
                        s.leaveVoice();
                        render(c);
                    });
            voiceButtons.addView(leaveCall, Ui.margins(new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f), c, 8, 0, 0, 0));
        } else {
            LinearLayout joinCall = Ui.button(c, R.drawable.ic_lumina_sound,
                    Tr.t("team_voice_join", "Unirse a la llamada"), true, v -> {
                        if (!ensureMic(c)) return;
                        requestNotifications(c);
                        s.joinVoice();
                        render(c);
                    });
            voiceButtons.addView(joinCall, Ui.matchWrap());
        }
        voiceCard.addView(voiceButtons, Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 0));

        if (!talking) {
            TextView joinHint = Ui.text(c, Tr.t("team_voice_join_hint", "Únete para hablar en grupo, como una llamada."), 11.5f, Ui.TEXT_3);
            joinHint.setPadding(0, Ui.dp(c, 6), 0, 0);
            voiceCard.addView(joinHint, Ui.matchWrap());
        }
        if (talking) root.postDelayed(voiceTick, 250);

        content.addView(Ui.sectionLabel(c, Tr.t("team_chat", "Chat del equipo")));
        LinearLayout chatCard = card(c, content);
        chatBox = Ui.column(c);
        chatCard.addView(chatBox, Ui.matchWrap());
        for (TeamClient.Chat msg : s.chat()) chatBox.addView(chatRow(c, msg), Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 6));
        LinearLayout send = Ui.row(c);
        chatInput = Ui.input(c, "", Tr.t("team_chat_hint", "Escribe un mensaje…"));
        chatInput.setSingleLine(true);
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (!chatDraft.isEmpty()) {
            chatInput.setText(chatDraft);
            chatInput.setSelection(chatDraft.length());
        }
        send.addView(chatInput, Ui.weight(1));
        LinearLayout sendBtn = Ui.button(c, R.drawable.ic_lumina_send, Tr.t("team_send", "Enviar"), true, v -> {
            String text = chatInput.getText().toString();
            if (text.trim().isEmpty()) return;
            s.sendChat(text);
            chatInput.setText("");
        });
        ((LinearLayout.LayoutParams) sendBtn.getLayoutParams()).setMarginStart(Ui.dp(c, 8));
        send.addView(sendBtn);
        chatCard.addView(send, Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        final boolean keepBottom = follow;
        final int keepY = scrollY;
        root.post(() -> {
            if (keepBottom) root.fullScroll(View.FOCUS_DOWN);
            else root.scrollTo(0, keepY);
        });
    }

    private View memberRow(Context c, TeamClient.Member member, TeamSettings.Membership me, TeamSession s) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        View dot = new View(c);
        dot.setBackground(Ui.rounded(member.online ? Ui.SUCCESS : Ui.TEXT_3, Ui.dp(c, 4)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8));
        dlp.setMarginEnd(Ui.dp(c, 8));
        row.addView(dot, dlp);
        String memberName = member.name == null ? "" : member.name;
        boolean meRow = member.id != null && member.id.equals(me.memberId);
        TextView name = Ui.text(c, memberName + (meRow ? " (" + Tr.t("team_you", "tú") + ")" : ""), 14, Ui.TEXT);
        row.addView(name, Ui.weight(1));
        row.addView(Ui.text(c, roleName(member.role), 12, Ui.ACCENT));
        if (me.canManage() && !"owner".equals(member.role) && !meRow) {
            row.setClickable(true);
            row.setOnClickListener(v -> memberMenu(c, member, s));
        }
        return row;
    }

    private View presenceRow(Context c, TeamClient.Member member, TeamClient.Presence p, TeamSession s) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 4), Ui.dp(c, 5), Ui.dp(c, 4), Ui.dp(c, 5));
        String memberName = member.name == null ? "" : member.name;
        boolean meRow = member.id != null && member.id.equals(s.membership() == null ? null : s.membership().memberId);
        TextView name = Ui.text(c, memberName + (meRow ? " (" + Tr.t("team_you", "tú") + ")" : ""), 13.5f, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name, Ui.weight(1));
        String where;
        if ("scene".equals(p.area)) where = Tr.t("team_now_scene", "Escena:") + " " + p.scene;
        else where = Tr.t("team_now_file", "Archivo:") + " " + shortPath(p.file);
        TextView location = Ui.text(c, where, 12, Ui.ACCENT);
        location.setMaxLines(1);
        location.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(location);
        if (!meRow) {
            row.setClickable(true);
            row.setOnClickListener(v -> follow(c, p));
        }
        return row;
    }

    private void follow(Context c, TeamClient.Presence p) {
        if (p == null) return;
        if ("scene".equals(p.area) && p.scene != null && !p.scene.isEmpty()) {
            Bridge.gl(() -> Bridge.controls().actionOpenScene(p.scene));
            return;
        }
        if ("code".equals(p.area) && p.file != null && new File(p.file).isFile()) {
            shell.host().openJava(p.file);
        }
    }

    private String shortPath(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 && slash + 1 < p.length() ? p.substring(slash + 1) : p;
    }

    private void memberMenu(Context c, TeamClient.Member member, TeamSession s) {
        String[] items = {
                Tr.t("team_make_editor", "Permiso: editor"),
                Tr.t("team_make_viewer", "Permiso: lector"),
                Tr.t("team_make_admin", "Permiso: admin"),
                Tr.t("team_remove", "Quitar del equipo")
        };
        Dialogs.choose(c, member.name, items, index -> {
            if (index == 3) {
                Dialogs.confirm(c, Tr.t("team_remove", "Quitar del equipo"), member.name, null, () -> s.removeMember(member.id, null));
                return;
            }
            String role = index == 0 ? "editor" : index == 1 ? "viewer" : "admin";
            s.setRole(member.id, role, null);
        });
    }

    private View chatRow(Context c, TeamClient.Chat msg) {
        if (msg == null) return new View(c);
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        box.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 8)));
        LinearLayout head = Ui.row(c);
        TextView name = Ui.text(c, msg.name == null ? "" : msg.name, 12.5f, Ui.ACCENT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(name, Ui.weight(1));
        head.addView(Ui.text(c, clock.format(new Date(msg.at)), 11, Ui.TEXT_3));
        box.addView(head, Ui.matchWrap());
        TextView text = Ui.text(c, msg.text == null ? "" : msg.text, 14, Ui.TEXT);
        text.setTextIsSelectable(true);
        text.setPadding(0, Ui.dp(c, 3), 0, 0);
        box.addView(text, Ui.matchWrap());
        TextView translate = Ui.text(c, Tr.t("team_translate", "Traducir"), 11.5f, Ui.ACCENT);
        translate.setPadding(0, Ui.dp(c, 4), 0, 0);
        translate.setOnClickListener(v -> TeamSession.get().translate(msg.text == null ? "" : msg.text, msg.lang, Locale.getDefault().getLanguage(), translated -> {
            if (translated == null || translated.trim().isEmpty()) {
                Ui.toast(c, Tr.t("team_translate_fail", "No se pudo traducir"));
            } else {
                Dialogs.longText(c, Tr.t("team_translation", "Traducción"), translated);
            }
        }));
        box.addView(translate, Ui.matchWrap());
        return box;
    }

    private View voiceTile(Context c, String id, TeamSession s, int avatarDp) {
        TeamClient.Member member = memberById(s, id);
        String name = displayName(member, id);
        String role = member != null ? roleName(member.role) : "";
        boolean muted = s.voiceMutedFor(id);
        LinearLayout tile = Ui.column(c);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        tile.setMinimumHeight(Ui.dp(c, avatarDp == 68 ? 150 : 112));
        tile.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 12), Ui.BORDER, Ui.dp(c, 1)));
        AvatarView avatar = new AvatarView(c);
        avatar.setProfile(name, id, avatarDp);
        voiceAvatars.put(id, avatar);
        voiceSpeaking.put(id, false);
        tile.addView(avatar);
        LinearLayout nameRow = Ui.row(c);
        nameRow.setGravity(Gravity.CENTER);
        nameRow.setPadding(0, Ui.dp(c, 8), 0, 0);
        TextView label = Ui.text(c, name, avatarDp == 68 ? 15 : 13, Ui.TEXT);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(label);
        if (muted) {
            ImageView mic = Ui.smallIcon(c, R.drawable.ic_lumina_mic_off, Ui.TEXT_3, 15);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mic.getLayoutParams();
            lp.setMarginStart(Ui.dp(c, 4));
            nameRow.addView(mic, lp);
        }
        tile.addView(nameRow, Ui.matchWrap());
        if (!role.isEmpty()) {
            TextView sub = Ui.text(c, role, 11, Ui.ACCENT);
            sub.setPadding(0, Ui.dp(c, 2), 0, 0);
            tile.addView(sub, Ui.matchWrap());
        }
        tile.setClickable(true);
        tile.setOnClickListener(v -> profileDialog(c, id, s));
        return tile;
    }

    private void profileDialog(Context c, String id, TeamSession s) {
        TeamClient.Member member = memberById(s, id);
        String name = displayName(member, id);
        String role = member == null ? "" : roleName(member.role);
        if (role.isEmpty() && s.membership() != null && id != null && id.equals(s.membership().memberId)) {
            role = roleName(s.membership().role);
        }
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0);
        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.CENTER);
        AvatarView avatar = new AvatarView(c);
        avatar.setProfile(name, id, 72);
        top.addView(avatar);
        box.addView(top, Ui.matchWrap());
        TextView title = Ui.text(c, name, 20, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(c, 12), 0, 0);
        box.addView(title, Ui.matchWrap());
        if (!role.isEmpty()) {
            TextView sub = Ui.text(c, Tr.t("team_profile_role", "Permiso:") + " " + role, 12, Ui.ACCENT);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, Ui.dp(c, 3), 0, 0);
            box.addView(sub, Ui.matchWrap());
        }
        boolean self = s.membership() != null && id != null && id.equals(s.membership().memberId);
        boolean inCall = s.voiceMembers().contains(id) || (self && s.inVoice());
        String status;
        if (inCall) {
            status = s.voiceMutedFor(id)
                    ? Tr.t("team_profile_in_call_muted", "En llamada · micrófono silenciado")
                    : Tr.t("team_profile_in_call", "En llamada");
        } else if (member != null && member.online) {
            status = Tr.t("team_profile_online", "En línea");
        } else {
            status = Tr.t("team_profile_offline", "Desconectado");
        }
        TextView state = Ui.text(c, status, 13, inCall ? Ui.SUCCESS : Ui.TEXT_3);
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, Ui.dp(c, 10), 0, 0);
        box.addView(state, Ui.matchWrap());
        TextView sid = Ui.text(c, "ID: " + shortId(id), 11, Ui.TEXT_3);
        sid.setGravity(Gravity.CENTER);
        sid.setTypeface(Typeface.MONOSPACE);
        sid.setPadding(0, Ui.dp(c, 8), 0, 0);
        box.addView(sid, Ui.matchWrap());
        new AlertDialog.Builder(c)
                .setTitle(Tr.t("team_profile", "Perfil"))
                .setView(box)
                .setPositiveButton(Tr.t("team_close", "Cerrar"), null)
                .show();
    }

    private TeamClient.Member memberById(TeamSession s, String id) {
        if (id == null) return null;
        for (TeamClient.Member member : s.members()) {
            if (member != null && id.equals(member.id)) return member;
        }
        return null;
    }

    private String displayName(TeamClient.Member member, String id) {
        if (member != null && member.name != null && !member.name.trim().isEmpty()) return member.name;
        return shortId(id);
    }

    private void requestNotifications(Context c) {
        if (Build.VERSION.SDK_INT < 33) return;
        Activity activity = shell.activity();
        if (activity == null) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        try {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY_REQUEST);
        } catch (Throwable ignored) {
        }
    }

    private boolean ensureMic(Context c) {
        Activity activity = shell.activity();
        if (activity == null) return false;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST);
        Ui.toast(c, Tr.t("team_mic_permission", "Permite el micrófono para hablar"));
        return false;
    }

    private void createDialog(Context c) {
        if (projectPath == null) {
            Ui.toast(c, Tr.t("team_need_project", "Abre un proyecto antes de crear el equipo"));
            return;
        }
        String project = new File(projectPath).getName();
        Dialogs.input(c, Tr.t("team_create", "Crear equipo"), Tr.t("team_name", "Nombre del equipo"), project, v -> v.trim().isEmpty() ? Tr.t("team_need_name", "Escribe un nombre") : null, room -> {
            Dialogs.input(c, Tr.t("team_your_name", "Tu nombre"), "", android.os.Build.MODEL, v -> v.trim().isEmpty() ? Tr.t("team_need_name", "Escribe un nombre") : null, name -> {
                TeamSession.get().create(room.trim(), name.trim(), error -> {
                    if (error != null) Ui.toast(c, error);
                    render(c);
                });
            });
        });
    }

    private void joinDialog(Context c) {
        if (projectPath == null) {
            Ui.toast(c, Tr.t("team_need_project", "Abre un proyecto antes de unirte a un equipo"));
            return;
        }
        Dialogs.input(c, Tr.t("team_join", "Unirme con una invitación"), Tr.t("team_invite_paste", "Pega sala#código"), "", v -> {
            String value = v.trim();
            int hash = value.indexOf('#');
            return hash <= 0 || hash == value.length() - 1 ? Tr.t("team_bad_invite", "Formato: sala#código") : null;
        }, value -> {
            String[] parts = value.trim().split("#", 2);
            Dialogs.input(c, Tr.t("team_your_name", "Tu nombre"), "", android.os.Build.MODEL, v -> v.trim().isEmpty() ? Tr.t("team_need_name", "Escribe un nombre") : null, name -> {
                TeamSession.get().join(parts[0].trim(), parts[1].trim(), name.trim(), error -> {
                    if (error != null) Ui.toast(c, error);
                    render(c);
                });
            });
        });
    }

    private void shareInvite(Context c, TeamSettings.Membership m) {
        String body = Tr.t("team_email_body", "Te invito a colaborar en mi proyecto de Nebulon Engine.\n\nAbre Nebulon Engine → Colaborar → Unirme con una invitación y pega:\n\n")
                + m.inviteText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, Tr.t("team_email_subject", "Invitación a colaborar en Nebulon Engine"));
        intent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            c.startActivity(Intent.createChooser(intent, Tr.t("team_email_invite", "Invitar por correo")));
        } catch (Throwable t) {
            Dialogs.copy(c, m.inviteText());
        }
    }

    private void publish(Context c) {
        if (projectPath == null) return;
        TeamSession.get().seedProject(new File(projectPath), changed -> {
            Ui.toast(c, changed == null || changed.isEmpty()
                    ? Tr.t("team_published_none", "El proyecto ya estaba publicado")
                    : Tr.t("team_published", "Publicados") + ": " + changed.size());
        });
    }

    private LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 12), Ui.BORDER, Ui.dp(c, 1)));
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 12));
        parent.addView(card, Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 4));
        return card;
    }

    private String stateText(TeamSession s) {
        switch (s.state()) {
            case "connected": return Tr.t("team_connected", "En vivo");
            case "connecting": return Tr.t("team_connecting", "Conectando…");
            case "offline": return Tr.t("team_offline", "Sin conexión");
            default: return s.hasRoom() ? Tr.t("team_idle", "Equipo guardado") : Tr.t("team_no_room", "Sin equipo");
        }
    }

    private String roleName(String role) {
        if ("owner".equals(role)) return Tr.t("team_role_owner", "Dueño");
        if ("admin".equals(role)) return Tr.t("team_role_admin", "Admin");
        if ("editor".equals(role)) return Tr.t("team_role_editor", "Editor");
        if ("viewer".equals(role)) return Tr.t("team_role_viewer", "Lector");
        return role == null ? "" : role;
    }

    private String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 10 ? id : id.substring(0, 10) + "…";
    }

    private String voiceNames(List<String> ids, List<TeamClient.Member> members) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            String name = null;
            for (TeamClient.Member member : members) {
                if (member != null && id != null && id.equals(member.id)) {
                    name = member.name;
                    break;
                }
            }
            if (name == null || name.trim().isEmpty()) name = shortId(id);
            if (out.length() > 0) out.append(", ");
            out.append(name);
        }
        return out.toString();
    }

    @Override
    public void onState(TeamSession session) {
        Ui.ui(() -> {
            if (shell.activity() != null) render(shell.activity());
        });
    }

    @Override
    public void onMembers(TeamSession session) {
        Ui.ui(() -> {
            if (shell.activity() != null) render(shell.activity());
        });
    }

    @Override
    public void onPresence(TeamSession session) {
        Ui.ui(() -> {
            if (shell.activity() != null) render(shell.activity());
        });
    }

    @Override
    public void onVoice(TeamSession session) {
        Ui.ui(() -> {
            if (shell.activity() != null) render(shell.activity());
        });
    }

    @Override
    public void onChat(TeamSession session, TeamClient.Chat message) {
        Ui.ui(() -> {
            Context c = shell.activity();
            if (c == null) return;
            if (chatBox == null) {
                render(c);
                return;
            }
            chatBox.addView(chatRow(c, message), Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 6));
            if (root != null) root.post(() -> root.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void onText(TeamSession session, String path, String text) {
    }

    @Override
    public void onError(TeamSession session, String message) {
        Ui.ui(() -> {
            Context c = shell.activity();
            if (c != null) Ui.toast(c, message);
        });
    }
}
