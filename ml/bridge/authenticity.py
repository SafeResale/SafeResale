"""M3 bridge shim: runs the real ViT AI-vs-human authenticity classifier inside
WSL. Reads JSON from stdin, prints JSON on stdout. Weights come from the HF
cache on first use (~343 MB) and are reused afterwards."""
import json
import os
import sys


def main() -> None:
    payload = json.load(sys.stdin)
    os.chdir(payload["change_cwd"] or os.getcwd())
    m3 = os.path.join(os.getcwd(), "ml", "m3_authenticity")
    if m3 not in sys.path:
        sys.path.insert(0, m3)

    from provider import provider_for  # type: ignore

    provider = provider_for(
        simulated=False,
        model_id=payload.get("model_id") or "dima806/ai_vs_human_generated_image_detection",
    )
    results = []
    try:
        for im in payload.get("images", []):
            r = provider.verify([im["image"]])
            doc = r[0].to_dict()
            doc["image"] = str(im["image"]).replace("\\", "/").rsplit("/", 1)[-1]
            doc["orig_image"] = im["image"]
            doc["image_id"] = im.get("image_id")
            results.append(doc)
    finally:
        provider.close()

    print(json.dumps({
        "model_version": provider.model_version,
        "simulated": provider.simulated(),
        "results": results,
    }))


if __name__ == "__main__":
    main()