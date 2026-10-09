"use client";

import { ContentManager } from "@/components/content-manager";

export default function BlogsPage() {
  return (
    <ContentManager
      cfg={{
        plural: "Blogs",
        singular: "Blog",
        api: "blogs",
        title: (it) => it.title || "Untitled",
        subtitle: (it) => `${it.excerpt || ""}${it.slug ? ` · /${it.slug}` : ""}`,
        fields: [
          { key: "title", label: "Title" },
          { key: "slug", label: "Slug (auto-generated if empty)" },
          { key: "excerpt", label: "Excerpt", type: "textarea" },
          { key: "content", label: "Content", type: "textarea" },
          { key: "cover_url", label: "Cover image URL" },
        ],
        defaults: { title: "", excerpt: "", content: "", cover_url: "", status: "draft" },
      }}
    />
  );
}