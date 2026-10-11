"""AetherPort for Windows — native desktop UI, dark surfaces and green accents."""
from __future__ import annotations
import json
import os
import queue
import sys
import threading
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from .core import (VERSION, VariableVault, data_dir, list_archives, delete_archive,
                   normalize_repo, repo_variable_name, publish_zip, download_hf, mask,
                   portable_backup_encrypt, validate_branch, repository_kind)
from .google_auth import authorize, DEFAULT_SCOPES
from .updates import find_windows_update, download_installer, launch_installer

BG = '#050808'
SURFACE = '#101a17'
SURFACE2 = '#1d2a25'
FIELD = '#15201c'
GREEN = '#6ce4b7'
GREEN_DIM = '#2c6655'
TEXT = '#e1efe9'
MUTED = '#a5b7b0'
ERROR = '#ff8c88'
BORDER = '#456057'
FONT = 'Segoe UI'


def asset_path(path):
    root = Path(getattr(sys, '_MEIPASS', Path(__file__).parent.parent))
    return root / 'assets' / path


class Button(tk.Button):
    def __init__(self, parent, text, command, primary=False, **kwargs):
        super().__init__(parent, text=text, command=command, bg=GREEN if primary else SURFACE2,
                         fg='#102b22' if primary else TEXT, activebackground='#a0ffdc' if primary else GREEN_DIM,
                         activeforeground='#122d22' if primary else TEXT, disabledforeground='#71857d',
                         borderwidth=0, relief='flat', cursor='hand2', font=(FONT, 11, 'bold'),
                         padx=18, pady=11, **kwargs)


class Field(tk.Entry):
    def __init__(self, parent, textvariable=None, secret=False, **kwargs):
        super().__init__(parent, textvariable=textvariable, show='•' if secret else '',
                         bg=FIELD, fg=TEXT, insertbackground=GREEN,
                         disabledbackground=SURFACE2, disabledforeground=MUTED,
                         highlightbackground=BORDER, highlightcolor=GREEN, highlightthickness=1,
                         borderwidth=0, relief='flat', font=(FONT, 12), **kwargs)
        self.configure(width=48)


class ScrollBox(tk.Frame):
    def __init__(self, master, height=None, **kwargs):
        super().__init__(master, bg=BG, **kwargs)
        canvas = tk.Canvas(self, bg=BG, bd=0, highlightthickness=0, height=height or 400)
        scrollbar = tk.Scrollbar(self, command=canvas.yview, troughcolor=BG, bg=SURFACE2,
                                 activebackground=GREEN_DIM)
        canvas.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side='right', fill='y')
        canvas.pack(side='left', fill='both', expand=True)
        self.inner = tk.Frame(canvas, bg=BG)
        self.inner_id = canvas.create_window((0,0), window=self.inner, anchor='nw')
        self.inner.bind('<Configure>', lambda e: canvas.configure(scrollregion=canvas.bbox('all')))
        canvas.bind('<Configure>', lambda e: canvas.itemconfig(self.inner_id, width=e.width))
        def scroll(e):
            canvas.yview_scroll(-int(e.delta / 120) if e.delta else 0, 'units')
        def bind_wheel(widget):
            widget.bind('<MouseWheel>', scroll, add='+')
            for child in widget.winfo_children(): bind_wheel(child)
        self.bind_wheel = lambda: bind_wheel(self.inner)
        self.canvas = canvas


class Modal(tk.Toplevel):
    def __init__(self, app, title, width=580, height=540):
        super().__init__(app)
        self.app = app
        self.configure(bg=SURFACE)
        self.title(title)
        self.transient(app)
        self.resizable(True, True)
        self.minsize(400, 300)
        app.update_idletasks()
        x = app.winfo_rootx() + max(0, (app.winfo_width() - width)//2)
        y = app.winfo_rooty() + max(0, (app.winfo_height() - height)//2)
        self.geometry(f'{width}x{height}+{x}+{y}')
        self.protocol('WM_DELETE_WINDOW', self.destroy)
        self.grab_set()
        self.bind('<Escape>', lambda e: self.destroy())
        self.body = tk.Frame(self, bg=SURFACE, padx=25, pady=18)
        self.body.pack(fill='both',expand=True)
        label(self.body, title, 21, bold=True, bg=SURFACE).pack(anchor='w', pady=(0,12))


def label(parent, text, size=11, bold=False, color=TEXT, bg=None, wrap=None):
    return tk.Label(parent, text=text, bg=bg or parent.cget('bg'), fg=color,
                    font=(FONT,size,'bold' if bold else 'normal'), anchor='w', justify='left',
                    wraplength=wrap or 0)


def field_line(parent, title, variable, secret=False):
    group = tk.Frame(parent, bg=parent.cget('bg'))
    group.pack(fill='x', pady=9)
    label(group,title,10,color=MUTED).pack(anchor='w',pady=(0,6))
    entry = Field(group, textvariable=variable, secret=secret)
    entry.pack(fill='x',ipady=9)
    return entry


def safe_error(error: Exception) -> str:
    text = str(error).strip() or type(error).__name__
    return text[:600]


class AetherPortWindow(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f'AetherPort {VERSION}')
        self.configure(bg=BG)
        theme = ttk.Style(self)
        theme.theme_use('clam')
        theme.configure('TCombobox', fieldbackground=FIELD, background=SURFACE2,
                        foreground=TEXT, arrowcolor=GREEN, bordercolor=BORDER,
                        lightcolor=BORDER, darkcolor=BORDER, selectbackground=GREEN_DIM,
                        selectforeground=TEXT)
        theme.map('TCombobox', fieldbackground=[('readonly',FIELD)],
                  foreground=[('readonly',TEXT)], background=[('readonly',SURFACE2)])
        self.option_add('*TCombobox*Listbox.background', SURFACE2)
        self.option_add('*TCombobox*Listbox.foreground', TEXT)
        self.geometry('1040x740')
        self.minsize(750, 550)
        try: self.iconbitmap(default=str(asset_path('aetherport.ico')))
        except (tk.TclError, FileNotFoundError): pass
        self.vault = VariableVault()
        self.settings_file = data_dir()/'settings.json'
        try: self.settings = json.loads(self.settings_file.read_text('utf-8'))
        except (OSError, ValueError): self.settings = {}
        self.zip_dir = Path(self.settings.get('zip_folder') or Path.home()/'Downloads')
        self.active_zip: Path | None = None
        self.active_tab = 'GitHub'
        self.hf_mode = 'Publish'
        self.busy = False
        self.jobs = queue.Queue()
        self.google_tokens = None
        self.hf_prepared_zip = None
        self.update_available = None
        self._render_shell()
        self.show_page('GitHub')
        self.after(75, self._drain_jobs)

    def _save_settings(self):
        self.settings_file.parent.mkdir(parents=True,exist_ok=True)
        temp = self.settings_file.with_suffix('.tmp')
        temp.write_text(json.dumps(self.settings,indent=2),encoding='utf-8')
        temp.replace(self.settings_file)

    def _render_shell(self):
        self.sidebar = tk.Frame(self, bg=SURFACE, width=208)
        self.sidebar.pack(side='left', fill='y')
        self.sidebar.pack_propagate(False)
        label(self.sidebar,'AetherPort',20,True, bg=SURFACE).pack(anchor='w',padx=22,pady=(33,8))
        label(self.sidebar, f'WINDOWS  •  v{VERSION}', 9, color=GREEN,bg=SURFACE).pack(anchor='w',padx=22,pady=(0,28))
        self.nav_buttons = {}
        for name in ('GitHub','Hugging Face','Google','Variables','Updates','Settings'):
            button = Button(self.sidebar,name,lambda page=name: self.show_page(page))
            button.configure(anchor='w', padx=21, pady=13)
            button.pack(fill='x',padx=12,pady=4)
            self.nav_buttons[name]=button
        label(self.sidebar, 'Private credentials\nStored locally',10,color=MUTED,bg=SURFACE).pack(anchor='w',padx=23,side='bottom',pady=24)
        right = tk.Frame(self,bg=BG)
        right.pack(side='left',fill='both',expand=True)
        top = tk.Frame(right,bg=BG,padx=33,pady=21)
        top.pack(fill='x')
        self.page_title = label(top, 'GitHub',23,True)
        self.page_title.pack(side='left')
        self.status = label(top,'',10,color=MUTED)
        self.status.pack(side='right')
        self.main_scroll = ScrollBox(right)
        self.main_scroll.pack(fill='both',expand=True,padx=28,pady=(0,20))
        self.content=self.main_scroll.inner

    def show_page(self,page):
        self.active_tab=page
        self.page_title.configure(text=page)
        for name,widget in self.nav_buttons.items():
            widget.configure(bg=GREEN_DIM if name==page else SURFACE2, fg=TEXT)
        for child in self.content.winfo_children(): child.destroy()
        {'GitHub':self._github, 'Hugging Face':self._hf, 'Google':self._google,
         'Variables':self._variables,'Updates':self._updates,'Settings':self._settings}[page]()
        self.main_scroll.canvas.yview_moveto(0)
        self.main_scroll.bind_wheel()

    def section(self,heading,detail=''):
        label(self.content,heading,23,True).pack(anchor='w',pady=(9,5))
        if detail: label(self.content,detail,11,color=MUTED,wrap=650).pack(anchor='w',pady=(0,18))

    def card(self):
        box = tk.Frame(self.content,bg=SURFACE,padx=22,pady=22)
        box.pack(fill='x',pady=8)
        return box

    def _task(self,task,success=None):
        if self.busy: return
        self.busy=True
        self.status.configure(text='Working…',fg=GREEN)
        def run():
            try: self.jobs.put((True,task(),success))
            except Exception as exc: self.jobs.put((False,exc,success))
        threading.Thread(target=run,daemon=True).start()

    def _drain_jobs(self):
        try:
            while True:
                ok,payload,callback=self.jobs.get_nowait()
                self.busy=False
                self.status.configure(text='')
                if ok:
                    if callback: callback(payload)
                else:
                    messagebox.showerror('AetherPort — operation failed',safe_error(payload),parent=self)
        except queue.Empty: pass
        self.after(75,self._drain_jobs)

    def _progress(self,message):
        self.after(0,lambda: self.status.configure(text=message[:43],fg=GREEN))

    def _archive_field(self,container):
        row=tk.Frame(container,bg=container.cget('bg'))
        row.pack(fill='x',pady=(4,9))
        Button(row,self.active_zip.name if self.active_zip and self.active_zip.exists() else 'Choose project ZIP',
               self.choose_zip).pack(side='left',fill='x',expand=True)
        Button(row,'Refresh',lambda: self.choose_zip()).pack(side='left',padx=(12,0))
        label(container,f'{self.zip_dir}  •  newest ZIPs first',10,color=MUTED,wrap=600).pack(anchor='w',pady=(0,5))

    def choose_zip(self):
        dialog = Modal(self,'Choose project ZIP',width=650,height=580)
        label(dialog.body,'Choose a ZIP to publish. Files are shown newest first.',10,color=MUTED,bg=SURFACE).pack(anchor='w',pady=(0,12))
        listing=ScrollBox(dialog.body)
        listing.pack(fill='both',expand=True)
        def refresh():
            for child in listing.inner.winfo_children(): child.destroy()
            try: records = list_archives(self.zip_dir)
            except OSError as exc:
                label(listing.inner,safe_error(exc),color=ERROR).pack(anchor='w')
                return
            if not records: label(listing.inner,'No ZIP files found in this folder.',color=MUTED).pack(pady=28)
            for archive in records:
                row=tk.Frame(listing.inner,bg=SURFACE2,padx=12,pady=9)
                row.pack(fill='x',pady=5)
                sub=tk.Frame(row,bg=SURFACE2)
                sub.pack(side='left',fill='x',expand=True)
                def select(a=archive):
                    self.active_zip=a.path
                    dialog.destroy()
                    self.show_page(self.active_tab)
                choose=Button(sub,archive.name,select,primary=self.active_zip==archive.path)
                choose.configure(anchor='w',wraplength=400,justify='left',padx=10,pady=8)
                choose.pack(fill='x')
                timestamp=datetime.fromtimestamp(archive.modified).strftime('%b %d, %Y %I:%M %p')
                label(sub,timestamp,9,color=MUTED,bg=SURFACE2).pack(anchor='w',padx=8)
                def remove(a=archive):
                    if messagebox.askyesno('Permanently delete ZIP',f'Delete {a.name} from disk?\nThis cannot be undone.',parent=dialog):
                        try:
                            delete_archive(a.path,self.zip_dir)
                            if self.active_zip==a.path:self.active_zip=None
                            refresh()
                        except (OSError,ValueError) as exc:
                            messagebox.showerror('Delete failed',safe_error(exc),parent=dialog)
                Button(row,'Delete',remove).pack(side='right',padx=8)
            listing.bind_wheel()
        refresh()
        bottom=tk.Frame(dialog.body,bg=SURFACE)
        bottom.pack(fill='x',pady=(15,0))
        Button(bottom,'Close',dialog.destroy).pack(side='right')
        Button(bottom,'ZIP folder Settings',lambda:(dialog.destroy(),self.show_page('Settings'))).pack(side='right',padx=9)

    def _repository_dialog(self,hf:bool,cb):
        repos=self.vault.repositories(hf)
        dialog=Modal(self,'Choose Hugging Face Space' if hf else 'Choose GitHub repository',width=570,height=460)
        label(dialog.body,'Select a saved repository.',11,color=MUTED,bg=SURFACE).pack(anchor='w',pady=(0,12))
        scroll=ScrollBox(dialog.body)
        scroll.pack(fill='both',expand=True)
        if not repos: label(scroll.inner,'No saved repositories. Add one in Variables.',color=MUTED).pack(pady=12)
        for repo in repos:
            Button(scroll.inner,repo,lambda r=repo:(cb(r),dialog.destroy())).pack(fill='x',pady=5)
        buttons=tk.Frame(dialog.body,bg=SURFACE)
        buttons.pack(fill='x',pady=(14,0))
        Button(buttons,'Cancel',dialog.destroy).pack(side='right')
        Button(buttons,'Manage in Variables',lambda:(dialog.destroy(),self.show_page('Variables'))).pack(side='right',padx=8)
        scroll.bind_wheel()

    def _publish_ui(self,hf=False):
        box=self.card()
        self._archive_field(box)
        repository=tk.StringVar(value='')
        branch=tk.StringVar(value='main')
        commit=tk.StringVar(value='Small bug fixes')
        Button(box,'Choose saved repository',lambda:self._repository_dialog(hf,repository.set)).pack(anchor='w',pady=(14,0))
        field_line(box,'Repository (owner/repository)',repository)
        if hf:
            token_names=self.vault.hf_tokens()
            token=tk.StringVar(value=token_names[0] if token_names else '')
            label(box,'Hugging Face token variable',10,color=MUTED,bg=SURFACE).pack(anchor='w',pady=(10,4))
            combo=ttk.Combobox(box,textvariable=token,values=token_names,state='readonly',font=(FONT,11))
            combo.pack(fill='x',ipady=5)
        else: token=None
        field_line(box,'Branch',branch)
        field_line(box,'Commit message',commit)
        def publish():
            current=self.active_zip
            if not current or not current.is_file():
                messagebox.showerror('Select ZIP','Choose an existing ZIP first.',parent=self)
                return
            try:
                repo=normalize_repo(repository.get(),'hf' if hf else 'github')
                b=validate_branch(branch.get())
                vals=self.vault.all()
                secret=vals[token.get()] if hf else self.vault.github_token(repo)
            except (ValueError,KeyError) as exc:
                messagebox.showerror('Publish setup',safe_error(exc),parent=self)
                return
            if not messagebox.askyesno('Confirm repository publish',
                f'Publish {current.name} to {repo}:{b}?\n\nFiles absent from the ZIP will be removed in the new commit.',
                parent=self):
                return
            self._task(lambda:publish_zip(current,'hf' if hf else 'github',repo,secret,b,commit.get(),self._progress))
        Button(box,'Publish to Hugging Face' if hf else 'Publish to GitHub',publish,primary=True).pack(fill='x',pady=(14,0))

    def _github(self):
        self.section('Publish to GitHub','Publish the selected ZIP to the repository root. Commits use the matching GitHub token owner’s no-reply identity.')
        self._publish_ui()

    def _hf(self):
        bar=tk.Frame(self.content,bg=BG)
        bar.pack(anchor='w',pady=(8,18))
        for value in ('Publish','Download'):
            Button(bar,value,lambda v=value:self._change_hf_mode(v),primary=self.hf_mode==value).pack(side='left',padx=(0,10))
        self.section('Publish to Hugging Face' if self.hf_mode=='Publish' else 'Download Hugging Face Space',
                     'Publish the selected ZIP or download a repository snapshot as a ZIP.')
        if self.hf_mode=='Publish': self._publish_ui(hf=True)
        else:
            box=self.card()
            repo=tk.StringVar()
            branch=tk.StringVar(value='main')
            token_names=self.vault.hf_tokens()
            token=tk.StringVar(value=token_names[0] if token_names else '')
            Button(box,'Choose saved Space',lambda:self._repository_dialog(True,repo.set)).pack(anchor='w')
            field_line(box,'Space repository',repo)
            label(box,'Hugging Face token',10,color=MUTED,bg=SURFACE).pack(anchor='w',pady=(10,5))
            ttk.Combobox(box,textvariable=token,values=token_names,state='readonly').pack(fill='x',ipady=5)
            field_line(box,'Branch',branch)
            def download():
                path=filedialog.asksaveasfilename(parent=self,defaultextension='.zip',filetypes=[('ZIP archives','*.zip')],
                                                  initialfile=(repo.get().split('/')[-1] or 'space')+'-'+branch.get()+'.zip')
                if not path: return
                try: secret=self.vault.all()[token.get()]
                except KeyError:
                    messagebox.showerror('Missing token','Select a saved Hugging Face token.',parent=self);return
                self._task(lambda:download_hf(repo.get(),secret,branch.get(),Path(path),self._progress))
            Button(box,'Download ZIP snapshot',download,primary=True).pack(fill='x',pady=18)

    def _change_hf_mode(self,mode):
        self.hf_mode=mode
        self.show_page('Hugging Face')

    def _google(self):
        self.section('Google OAuth','Generate Google Drive credentials using a Desktop OAuth client. The browser opens for account authorization.')
        box=self.card()
        filename=tk.StringVar(value='No credentials.json selected')
        selected={'path':None}
        def choose():
            result=filedialog.askopenfilename(parent=self,filetypes=[('JSON files','*.json')])
            if result: selected['path']=Path(result);filename.set(Path(result).name)
        Button(box,'Choose credentials.json',choose).pack(anchor='w',pady=(0,5))
        label(box,'Select a Desktop application OAuth client (not a Web client).',10,color=MUTED,bg=SURFACE).pack(anchor='w')
        label(box,'File: ',10,color=MUTED,bg=SURFACE).pack(anchor='w',pady=(8,0))
        tk.Label(box,textvariable=filename,bg=SURFACE,fg=MUTED,font=(FONT,10)).pack(anchor='w')
        scopes=tk.StringVar(value=DEFAULT_SCOPES)
        field_line(box,'Google OAuth scopes (space-separated)',scopes)
        def generate():
            if not selected['path']:
                messagebox.showerror('Missing credentials','Choose credentials.json.',parent=self);return
            def success(result):
                self.google_tokens=result
                self.show_page('Google')
            self._task(lambda:authorize(selected['path'],scopes.get()),success)
        Button(box,'Generate Google tokens',generate,primary=True).pack(fill='x',pady=12)
        if self.google_tokens:
            row=tk.Frame(box,bg=SURFACE);row.pack(fill='x',pady=8)
            def save(idx,filename):
                dest=filedialog.asksaveasfilename(parent=self,initialfile=filename)
                if dest: Path(dest).write_bytes(self.google_tokens[idx])
            Button(row,'Save token.json',lambda:save(0,'token.json')).pack(side='left',padx=(0,10))
            Button(row,'Save token.pickle',lambda:save(1,'token.pickle')).pack(side='left')

    def _variables(self):
        if not hasattr(self,'var_tab'): self.var_tab='Credentials'
        row=tk.Frame(self.content,bg=BG)
        row.pack(anchor='w',pady=(8,14))
        for name in ('Credentials','Repositories'):
            Button(row,name,lambda n=name:self._change_var_tab(n),primary=self.var_tab==name).pack(side='left',padx=(0,12))
        if self.var_tab=='Credentials':self._credentials()
        else:self._repos()

    def _change_var_tab(self,name):
        self.var_tab=name;self.show_page('Variables')

    def _credentials(self):
        self.section('Credentials','Values are encrypted locally for your Windows account using Windows DPAPI.')
        bar=tk.Frame(self.content,bg=BG);bar.pack(fill='x',pady=(0,8))
        Button(bar,'Export credentials',self._export_credentials).pack(side='left',padx=(0,8))
        Button(bar,'Import credentials',self._import_credentials).pack(side='left')
        card=self.card()
        name=tk.StringVar();value=tk.StringVar()
        field_line(card,'Variable name',name)
        field_line(card,'Value',value,secret=True)
        def save():
            try:self.vault.put(name.get(),value.get())
            except ValueError as exc:messagebox.showerror('Invalid variable',safe_error(exc),parent=self);return
            self.show_page('Variables')
        Button(card,'Save variable',save,primary=True).pack(fill='x',pady=10)
        self.section('Saved credentials')
        for key,secret in sorted(self.vault.all().items()):
            if repository_kind(key): continue
            row=tk.Frame(self.content,bg=SURFACE,padx=16,pady=12);row.pack(fill='x',pady=5)
            info=tk.Frame(row,bg=SURFACE);info.pack(side='left',fill='x',expand=True)
            label(info,key,11,True,bg=SURFACE,wrap=420).pack(anchor='w')
            label(info,mask(secret),10,color=MUTED,bg=SURFACE).pack(anchor='w')
            def delete(n=key):
                if messagebox.askyesno('Delete credential',f'Delete {n}?',parent=self):self.vault.delete(n);self.show_page('Variables')
            Button(row,'Delete',delete).pack(side='right')

    def _repos(self):
        if not hasattr(self,'repo_kind'):self.repo_kind='github'
        bar=tk.Frame(self.content,bg=BG);bar.pack(anchor='w',pady=6)
        for kind in ('github','hf'):
            Button(bar,'GitHub' if kind=='github' else 'Hugging Face',lambda k=kind:self._set_repo_kind(k),primary=self.repo_kind==kind).pack(side='left',padx=(0,12))
        box=self.card()
        labelname=tk.StringVar();repo=tk.StringVar()
        field_line(box,'Repository label',labelname)
        field_line(box,'GitHub repository' if self.repo_kind=='github' else 'Space repository',repo)
        def save():
            try:self.vault.put(repo_variable_name(labelname.get(),self.repo_kind=='hf'),repo.get())
            except ValueError as exc:messagebox.showerror('Invalid repository',safe_error(exc),parent=self);return
            self.show_page('Variables')
        Button(box,'Save repository',save,primary=True).pack(fill='x',pady=10)
        for key,value in sorted(self.vault.all().items()):
            if repository_kind(key)!=self.repo_kind:continue
            row=tk.Frame(self.content,bg=SURFACE,padx=14,pady=12);row.pack(fill='x',pady=6)
            label(row,value,12,bg=SURFACE,wrap=430).pack(side='left',fill='x',expand=True)
            def edit(k=key,v=value):
                labelname.set(k.replace('GITHUB_REPOSITORY_','').replace('HF_REPOSITORY_',''))
                repo.set(v)
            Button(row,'Edit',edit).pack(side='right',padx=5)
            def delete(k=key):
                if messagebox.askyesno('Remove repository','Remove this saved repository?',parent=self):
                    self.vault.delete(k);self.show_page('Variables')
            Button(row,'Delete',delete).pack(side='right')

    def _set_repo_kind(self,kind):self.repo_kind=kind;self.show_page('Variables')

    def _export_credentials(self):
        dialog=Modal(self,'Export credentials',height=370,width=500)
        label(dialog.body,'Protect this portable backup with a password (at least 12 characters).',10,
              color=MUTED,bg=SURFACE,wrap=420).pack(anchor='w')
        pw=tk.StringVar(); confirm=tk.StringVar()
        field_line(dialog.body,'Backup password',pw,secret=True)
        field_line(dialog.body,'Confirm password',confirm,secret=True)
        def export():
            if pw.get()!=confirm.get():
                messagebox.showerror('Password mismatch','Passwords do not match.',parent=dialog);return
            try: payload=portable_backup_encrypt(self.vault.all(),pw.get())
            except ValueError as exc: messagebox.showerror('Backup failed',safe_error(exc),parent=dialog);return
            dest=filedialog.asksaveasfilename(parent=dialog,defaultextension='.aetherbackup',
                                             initialfile='aetherport-credentials.aetherbackup',filetypes=[('AetherPort Backup','*.aetherbackup')])
            if dest:Path(dest).write_bytes(payload); dialog.destroy()
        Button(dialog.body,'Export encrypted backup',export,primary=True).pack(fill='x',pady=13)

    def _import_credentials(self):
        source=filedialog.askopenfilename(parent=self,filetypes=[('AetherPort Backup','*.aetherbackup'),('All files','*.*')])
        if not source: return
        dialog=Modal(self,'Import credentials',height=370,width=510)
        label(dialog.body,'Import saved tokens and repositories from Android or Windows.',10,
              color=MUTED,bg=SURFACE).pack(anchor='w')
        pw=tk.StringVar(); field_line(dialog.body,'Backup password',pw,secret=True)
        replace=tk.BooleanVar(value=False)
        tk.Checkbutton(dialog.body,text='Replace matching credentials',variable=replace,bg=SURFACE,
                       fg=TEXT,activebackground=SURFACE,selectcolor=SURFACE2,font=(FONT,10)).pack(anchor='w',pady=12)
        def do_import():
            try:
                blob=Path(source).read_bytes()
                restored,skipped=self.vault.import_backup(blob,pw.get(),replace.get())
            except (OSError,ValueError) as exc:
                messagebox.showerror('Import failed',safe_error(exc),parent=dialog);return
            dialog.destroy();self.show_page('Variables')
            messagebox.showinfo('Import finished',f'Imported {restored} credentials; skipped {skipped} existing.',parent=self)
        Button(dialog.body,'Import encrypted backup',do_import,primary=True).pack(fill='x',pady=12)

    def _updates(self):
        self.section('Windows updates','Check the dedicated windows-stable GitHub release for an updated Windows installer.')
        box=self.card()
        repo=tk.StringVar(value=self.settings.get('update_repository','SiamTest/AetherPort'))
        field_line(box,'Update repository',repo)
        def check():
            try:self.settings['update_repository']=normalize_repo(repo.get(),'github');self._save_settings()
            except ValueError as exc:messagebox.showerror('Repository',safe_error(exc),parent=self);return
            token=''
            try: token=self.vault.github_token(repo.get())
            except ValueError: pass
            def done(update):
                self.update_available=update
                self.show_page('Updates')
                if update is None:messagebox.showinfo('Up to date',f'AetherPort {VERSION} is the current Windows version.',parent=self)
            self._task(lambda:find_windows_update(repo.get(),token),done)
        Button(box,'Check for updates',check,primary=True).pack(fill='x',pady=(14,0))
        if self.update_available:
            label(box,f'Windows {self.update_available["version"]} is available',14,True,bg=SURFACE).pack(anchor='w',pady=14)
            def download():
                update=self.update_available
                token=''
                try: token=self.vault.github_token(repo.get())
                except ValueError: pass
                destination=data_dir()/'updates'/update['name']
                def done(path):
                    if messagebox.askyesno('Install Windows update',f'Run the downloaded installer?\n{path.name}',parent=self):
                        try:launch_installer(path)
                        except Exception as exc:messagebox.showerror('Could not launch installer',safe_error(exc),parent=self)
                self._task(lambda:download_installer(update,destination,token),done)
            Button(box,'Download Windows installer',download,primary=True).pack(fill='x',pady=7)

    def _settings(self):
        self.section('Settings','Choose the folder containing project ZIP archives. AetherPort reads files directly without copying them.')
        box=self.card()
        folder=tk.StringVar(value=str(self.zip_dir))
        field_line(box,'ZIP folder',folder)
        def pick():
            value=filedialog.askdirectory(parent=self,initialdir=str(self.zip_dir))
            if value:folder.set(value)
        Button(box,'Browse folders',pick).pack(anchor='w',pady=12)
        def save():
            path=Path(folder.get()).expanduser()
            if not path.is_dir():messagebox.showerror('Missing folder','Choose an existing folder.',parent=self);return
            self.zip_dir=path.resolve();self.settings['zip_folder']=str(self.zip_dir);self._save_settings()
            self.active_zip=None;self.show_page('Settings')
        Button(box,'Save ZIP folder',save,primary=True).pack(fill='x',pady=7)
        label(box,'Deleting a ZIP in the chooser permanently removes the real file from this folder.',10,
              color=MUTED,bg=SURFACE,wrap=550).pack(anchor='w',pady=(12,0))


def main():
    app=AetherPortWindow()
    app.mainloop()

if __name__=='__main__':main()
