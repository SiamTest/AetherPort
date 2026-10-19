"""Native Tk smoke check. Run with a display; no account or network needed."""
from pathlib import Path
import sys
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'windows'))
from aetherport import app


def main():
    with TemporaryDirectory() as directory:
        app.data_dir = lambda: Path(directory)
        window = app.AetherPortWindow()
        errors = []
        window.report_callback_exception = lambda kind, value, traceback: errors.append(str(value))
        try:
            for width, height in ((1040, 740), (750, 550)):
                window.geometry(f'{width}x{height}')
                for page in window.nav_buttons:
                    window.show_page(page)
                    window.update()
                    assert window.content.winfo_width() > 400, page
                    for child in window.sidebar.winfo_children():
                        if isinstance(child, app.tk.Label):
                            assert child.winfo_reqwidth() <= child.winfo_width(), child.cget('text')
            calls = []
            button = app.Button(window.content, 'Keyboard action', lambda: calls.append(True))
            button.pack()
            window.update()
            button.configure(state='disabled')
            button._button.invoke()
            assert not calls, 'Disabled buttons must not invoke their action.'
            button.configure(state='normal')
            button._button.invoke()
            assert calls == [True]
            button._canvas.event_generate('<Button-1>', x=3, y=20)
            window.update()
            assert calls == [True, True], 'The rounded shell must also be clickable.'
            button._button.focus_force()
            window.update()
            assert window.focus_get() == button._button
            assert not errors, errors
        finally:
            window.destroy()
    print('Native UI: six pages, two window sizes, button states, shell clicks and focus passed.')


if __name__ == '__main__':
    main()
