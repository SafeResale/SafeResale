"use client"

import { AlertDialog, Button, Label } from "@heroui/react"

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  destructive?: boolean
  pending?: boolean
  onConfirm: () => void
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = "Continue",
  cancelLabel = "Cancel",
  destructive = false,
  pending = false,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <AlertDialog isOpen={open} onOpenChange={(v) => { if (!pending) onOpenChange(v) }}>
      <AlertDialog.Backdrop />
      <AlertDialog.Container>
        <AlertDialog.Dialog>
          <AlertDialog.Header>
            <AlertDialog.Icon />
            <AlertDialog.Heading>{title}</AlertDialog.Heading>
          </AlertDialog.Header>
          {description && (
            <AlertDialog.Body>
              <Label>{description}</Label>
            </AlertDialog.Body>
          )}
          <AlertDialog.Footer>
            <AlertDialog.CloseTrigger>
              <Button variant="ghost" isDisabled={pending}>{cancelLabel}</Button>
            </AlertDialog.CloseTrigger>
            <Button
              variant={destructive ? "danger" : "primary"}
              isDisabled={pending}
              onPress={onConfirm}
            >
              {pending ? "Working…" : confirmLabel}
            </Button>
          </AlertDialog.Footer>
          <AlertDialog.CloseTrigger />
        </AlertDialog.Dialog>
      </AlertDialog.Container>
    </AlertDialog>
  )
}
