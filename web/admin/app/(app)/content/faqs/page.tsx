"use client";

import { ContentManager } from "@/components/content-manager";

export default function FaqsPage() {
  return (
    <ContentManager
      cfg={{
        plural: "FAQs",
        singular: "FAQ",
        api: "faqs",
        title: (it) => it.question || "Untitled",
        subtitle: (it) => it.category || "",
        fields: [
          { key: "question", label: "Question" },
          { key: "answer", label: "Answer", type: "textarea" },
          { key: "category", label: "Category" },
          { key: "sort", label: "Sort order", type: "number" },
        ],
        defaults: { question: "", answer: "", category: "", sort: 0, status: "published" },
      }}
    />
  );
}