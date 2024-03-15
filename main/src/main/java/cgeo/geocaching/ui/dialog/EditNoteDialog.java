package cgeo.geocaching.ui.dialog;

import cgeo.geocaching.R;
import cgeo.geocaching.activity.Keyboard;
import cgeo.geocaching.models.Image;
import cgeo.geocaching.network.HttpRequest;
import cgeo.geocaching.network.HttpResponse;
import cgeo.geocaching.utils.ImageUtils;
import static cgeo.geocaching.connector.gc.GCAuthAPI.websiteReq;
import static cgeo.geocaching.utils.ProgressButtonDisposableHandler.getCircularProgressIndicatorDrawable;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.material.button.MaterialButton;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class EditNoteDialog extends AbstractFullscreenDialog {

    public static final String ARGUMENT_INITIAL_NOTE = "initialNote";
    public static final String ARGUMENT_INITIAL_PREVENT = "initialPrevent";
    public static final String ARGUMENT_INITIAL_UPLOAD_AVAILABLE = "initialUploadAvailable";

    private Toolbar toolbar;
    private EditText mEditText;
    private CheckBox mPreventCheckbox;

    public interface EditNoteDialogListener {
        void onFinishEditNoteDialog(String inputText, boolean preventWaypointsFromNote, boolean uploadNote);
        void onDismissEditNoteDialog();
    }

    /**
     * Create a new dialog to edit a note.
     * <em>This fragment must be inserted into an activity implementing the EditNoteDialogListener interface.</em>
     *
     * @param initialNote the initial note to insert in the edit dialog
     */
    public static EditNoteDialog newInstance(final String initialNote, final boolean preventWaypointsFromNote, final boolean connectorSupportsUpload) {
        final EditNoteDialog dialog = new EditNoteDialog();

        final Bundle arguments = new Bundle();
        arguments.putString(ARGUMENT_INITIAL_NOTE, initialNote);
        arguments.putBoolean(ARGUMENT_INITIAL_PREVENT, preventWaypointsFromNote);
        arguments.putBoolean(ARGUMENT_INITIAL_UPLOAD_AVAILABLE, connectorSupportsUpload);
        dialog.setArguments(arguments);

        return dialog;
    }

    @Override
    public View onCreateView(@NotNull final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View view = inflater.inflate(R.layout.fragment_edit_note, container, false);

        toolbar = view.findViewById(R.id.toolbar);

        ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
                registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        Image image = new Image.Builder().setUrl(uri).build();
                        final File imageFileForUpload = ImageUtils.scaleAndCompressImageToTemporaryFile(image.getUri(), 800, 75);
                        try (GCWebListingImageResponse imgResponse = websiteReq().uri("/api/proxy/web/v1/images/textFieldImages/GeocacheDescription")
                                .method(HttpRequest.Method.POST)
                                .bodyForm(null, "image", "image/jpeg", imageFileForUpload)
                                .requestJson(GCWebListingImageResponse.class).blockingGet()) {
                            if (imgResponse.url == null) {
                                Toast.makeText(this.getContext(), "Problem posting image, response is: " + imgResponse, Toast.LENGTH_LONG);
                            }
                            EditText note = view.findViewById(R.id.note);
                            note.append("\n"+imgResponse.url.replace(":443", ""));
                        }
                    }
                    MaterialButton v = view.findViewById(R.id.image_add_multi);
                    v.setEnabled(true);
                    v.setIcon(getContext().getDrawable(R.drawable.ic_menu_image_multi));
                });
        view.findViewById(R.id.image_add_multi).setOnClickListener(v -> {
            v.setEnabled(false);
            ((MaterialButton) v).setIcon(getCircularProgressIndicatorDrawable(v.getContext()));
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        return view;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class GCWebListingImageResponse extends HttpResponse {
        @JsonProperty("url")
        String url;

    }

    @Override
    public void onCreateOptionsMenu(final @NonNull Menu menu, final @NonNull MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.menu_ok_cancel, menu);
        menu.findItem(R.id.menu_item_save_and_upload).setVisible(getArguments().getBoolean(ARGUMENT_INITIAL_UPLOAD_AVAILABLE));
    }

    @Override
    public void onViewCreated(@NotNull final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        mEditText = view.findViewById(R.id.note);
        String initialNote = getArguments().getString(ARGUMENT_INITIAL_NOTE);
        if (initialNote != null) {
            // add a new line when editing existing text, to avoid accidental overwriting of the last line
            if (StringUtils.isNotBlank(initialNote) && !initialNote.endsWith("\n")) {
                initialNote = initialNote + "\n";
            }
            mEditText.setText(initialNote);
            Dialogs.moveCursorToEnd(mEditText);
            getArguments().remove(ARGUMENT_INITIAL_NOTE);
        }
        mPreventCheckbox = view.findViewById(R.id.preventWaypointsFromNote);
        final boolean preventWaypointsFromNote = getArguments().getBoolean(ARGUMENT_INITIAL_PREVENT);
        mPreventCheckbox.setChecked(preventWaypointsFromNote);

        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.setTitle(R.string.cache_personal_note);
        onCreateOptionsMenu(toolbar.getMenu(), new MenuInflater(getContext()));
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_item_save) {
                ((EditNoteDialogListener) requireActivity()).onFinishEditNoteDialog(mEditText.getText().toString(), mPreventCheckbox.isChecked(), false);
            } else if (item.getItemId() == R.id.menu_item_save_and_upload) {
                ((EditNoteDialogListener) requireActivity()).onFinishEditNoteDialog(mEditText.getText().toString(), mPreventCheckbox.isChecked(), true);
            }
            dismiss();
            return true;
        });

        Keyboard.show(requireActivity(), mEditText);
    }

    @Override
    public void onDismiss(final @NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        ((EditNoteDialogListener) requireActivity()).onDismissEditNoteDialog();
    }
}
