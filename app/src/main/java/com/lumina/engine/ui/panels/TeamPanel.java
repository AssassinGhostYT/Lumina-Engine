package com.lumina.engine.ui.panels;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lumina.engine.R;
import com.lumina.engine.team.TeamClient;
import com.lumina.engine.team.TeamSession;
import com.lumina.engine.team.TeamSettings;
import com.lumina.engine.ui.Bridge;
import com.lumina.engine.ui.Dialogs;
import com.lumina.engine.ui.EditorShell;
import com.lumina.engine.ui.PanelHost;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeamPanel implements PanelHost.Panel, TeamSession.Listener {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout content;
    private LinearLayout chatBox;
    private EditText chatInput;
    private String projectPath;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());

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

    @Override
    public void onHidden() {
    }

    public void dispose() {
        TeamSession.get().removeListener(this);
    }

    private void loadProject(Context c) {
        Bridge.query(() -> Bridge.editor() == null ? null : Bridge.editor().getProject().getPath(), path -> {
            if (path == null) {
                render(c);
                return;
            }
            if (!path.equals(projectPath)) {
                projectPath = path;
                TeamSession.get().load(c, path, () -> render(c));
            } else {
                render(c);
            }
        });
    }

    private void render(Context c) {
        if (content == null || c == null) return;
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

        content.addView(Ui.sectionLabel(c, Tr.t("team_chat", "Chat del equipo")));
        LinearLayout chatCard = card(c, content);
        chatBox = Ui.column(c);
        chatCard.addView(chatBox, Ui.matchWrap());
        for (TeamClient.Chat msg : s.chat()) chatBox.addView(chatRow(c, msg), Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 6));
        LinearLayout send = Ui.row(c);
        chatInput = Ui.input(c, "", Tr.t("team_chat_hint", "Escribe un mensaje…"));
        chatInput.setSingleLine(true);
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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
        root.post(() -> root.fullScroll(View.FOCUS_DOWN));
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
        translate.setOnClickListener(v -> TeamSession.get().translate(msg.text == null ? "" : msg.text, Locale.getDefault().getLanguage(), translated -> {
            if (translated == null || translated.trim().isEmpty()) {
                Ui.toast(c, Tr.t("team_translate_fail", "No se pudo traducir"));
            } else {
                Dialogs.longText(c, Tr.t("team_translation", "Traducción"), translated);
            }
        }));
        box.addView(translate, Ui.matchWrap());
        return box;
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
