package com.lumina.engine.ui.behaviors;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.lumina.engine.behaviors.BehaviorCatalog;
import com.lumina.engine.behaviors.BehaviorMeta;
import com.lumina.engine.behaviors.BehaviorTemplate;
import com.lumina.engine.behaviors.BehaviorPack;
import com.lumina.engine.ui.Dialogs;
import com.lumina.engine.ui.EditorShell;
import com.lumina.engine.ui.Tr;
import com.lumina.engine.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;

/** Creating, exporting and importing project behaviors (.lmb files). */
public final class BehaviorShare {
	private BehaviorShare() {}

	/** Writes Name.lmb to Lumina Engine/exports on the storage and opens the share sheet. */
	public static void export(Context c, String projectPath, BehaviorMeta m) {
		try {
			File dir = new File(Environment.getExternalStorageDirectory(), "Lumina Engine/exports");
			dir.mkdirs();
			File out = new File(dir, m.id + ".lmb");
			BehaviorPack.export(m, projectPath, out);
			Ui.toast(c, Tr.t("bhv_exported", "Saved") + ": Lumina Engine/exports/" + out.getName());
			Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", out);
			Intent s = new Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			c.startActivity(Intent.createChooser(s, out.getName()));
		} catch (Exception e) {
			Ui.toast(c, e.getMessage() == null ? e.toString() : e.getMessage());
		}
	}

	/** "New behavior": asks the name and the kind of element, writes the file from the template and opens it. */
	public static void create(EditorShell shell, String projectPath, Runnable done) {
		Context c = shell.activity();
		Dialogs.input(c, Tr.t("bhv_new", "New behavior"), Tr.t("bhv_new_hint", "Class name, starts with a capital letter (e.g. Wobble)"), "MyBehavior", v -> {
			if (!BehaviorTemplate.validName(v.trim())) return Tr.t("bhv_bad_name", "Use letters and digits, starting with a capital letter");
			if (new File(BehaviorCatalog.projectDir(projectPath), v.trim() + ".java").exists()) return Tr.t("ui_name_used", "Name already used");
			return null;
		}, name -> {
			String n = name.trim();
			Dialogs.choose(c, Tr.t("bhv_new_for", "Works on"), new String[] {Tr.t("bhv_f_all", "UI and physical"), Tr.t("bhv_f_ui", "UI"), Tr.t("bhv_f_physics", "Physical"), Tr.t("bhv_f_3d", "3D")}, i -> {
				String t = i == 1 ? "UI" : (i == 2 ? "PHYSICS" : (i == 3 ? "THREE_D" : "BOTH"));
				try {
					File f = new File(BehaviorCatalog.projectDir(projectPath), n + ".java");
					f.getParentFile().mkdirs();
					try (FileOutputStream o = new FileOutputStream(f)) {
						o.write(BehaviorTemplate.source(n, t).getBytes("UTF-8"));
					}
					if (done != null) done.run();
					shell.host().openJava(f.getAbsolutePath());
				} catch (Exception e) {
					Ui.toast(c, e.toString());
				}
			});
		});
	}

	/** Picks .lmb file(s) with the system picker and installs them. */
	public static void importPicker(EditorShell shell, String projectPath, Runnable done) {
		Context c = shell.activity();
		File tmp = new File(projectPath, ".lmb_import");
		tmp.mkdirs();
		shell.host().importFiles(tmp.getAbsolutePath(), () -> {
			File[] files = tmp.listFiles();
			if (files != null)
				for (File f : files) {
					if (f.getName().toLowerCase().endsWith("." + BehaviorPack.EXT)) installWithConfirm(c, projectPath, f, done);
					else f.delete();
				}
			tmp.delete();
		});
	}

	private static void installWithConfirm(Context c, String projectPath, File zip, Runnable done) {
		try {
			BehaviorPack.Info info = BehaviorPack.inspect(zip);
			boolean exists = new File(BehaviorCatalog.projectDir(projectPath), info.id + ".java").exists();
			String msg = info.name + "  v" + info.version + (info.author.isEmpty() ? "" : "\n" + Tr.t("bhv_by", "by") + " " + info.author)
					+ (info.description.isEmpty() ? "" : "\n\n" + info.description)
					+ (info.assets.isEmpty() ? "" : "\n\n" + info.assets.size() + " " + Tr.t("bhv_assets", "image/sound files"))
					+ (exists ? "\n\n⚠ " + Tr.t("bhv_replace_warn", "A behavior with this name already exists and will be replaced.") : "")
					+ "\n\n" + Tr.t("bhv_import_note", "It contains Java code that runs inside your game. Only import behaviors you trust.");
			Dialogs.confirm(c, Tr.t("bhv_import", "Import behavior"), msg, Tr.t("bhv_import_btn", "Import"), () -> {
				try {
					String id = BehaviorPack.install(zip, projectPath, true);
					zip.delete();
					Ui.toast(c, Tr.t("bhv_imported", "Imported") + ": " + id);
				} catch (Exception e) {
					Ui.toast(c, e.getMessage() == null ? e.toString() : e.getMessage());
				}
				if (done != null) done.run();
			});
		} catch (Exception e) {
			Ui.toast(c, Tr.t("bhv_bad_file", "Not a valid .lmb file") + ": " + e.getMessage());
			zip.delete();
		}
	}
}
