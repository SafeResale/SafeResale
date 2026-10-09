"use client";

import { ContentManager } from "@/components/content-manager";

export default function TipsPage() {
  return (
    <ContentManager
      cfg={{
        plural: "Tips",
        singular: "Tip",
        api: "tips",
        title: (it) => it.title || "Untitled",
        subtitle: (it) => it.category || "",
        fields: [
          { key: "title", label: "Title" },
          { key: "description", label: "Description", type: "textarea" },
          { key: "category", label: "Category" },
          { key: "sort", label: "Sort order", type: "number" },
        ],
        defaults: { title: "", description: "", category: "", sort: 0, status: "published" },
      }}
    />
  );
}