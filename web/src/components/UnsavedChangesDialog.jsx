import { ConfirmationDialog } from './ConfirmationDialog';
export function UnsavedChangesDialog({ control }) {
    return (<ConfirmationDialog open={control.isBlocked} eyebrow="Unsaved changes" title="Discard unsaved changes?" confirmLabel="Discard changes" busyLabel="Discarding…" onCancel={control.stay} onConfirm={control.discardAndContinue}>
      <p>Your unsaved patient changes will be lost. Stay on this page to review or save them.</p>
    </ConfirmationDialog>);
}
