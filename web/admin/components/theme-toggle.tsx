"use client"

import { Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"
import { Button, Dropdown, Label } from "@heroui/react"

export function ThemeToggle() {
  const { setTheme } = useTheme()
  return (
    <Dropdown>
      <Dropdown.Trigger>
        <Button variant="outline" isIconOnly aria-label="Toggle theme" className="h-9 w-9">
          <Sun className="size-4 scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
          <Moon className="absolute size-4 scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
        </Button>
      </Dropdown.Trigger>
      <Dropdown.Popover>
        <Dropdown.Menu onAction={(key) => setTheme(key as string)}>
          <Dropdown.Item id="light" textValue="Light">
            <Label>Light</Label>
          </Dropdown.Item>
          <Dropdown.Item id="dark" textValue="Dark">
            <Label>Dark</Label>
          </Dropdown.Item>
          <Dropdown.Item id="system" textValue="System">
            <Label>System</Label>
          </Dropdown.Item>
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  )
}
