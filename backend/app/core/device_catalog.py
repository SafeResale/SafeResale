"""Comprehensive Device and Vehicle Catalog Database for SafeResale.

Provides canonical brand, model, variant, hardware identifiers, and market price baselines
for:
- Mobiles (Apple, Samsung, Google, OnePlus, Xiaomi, Realme, Vivo, Oppo, Motorola)
- Laptops (Apple, Dell, HP, Lenovo, Asus, Acer, Microsoft)
- Cars (Maruti Suzuki, Hyundai, Tata, Mahindra, Toyota, Honda, Kia, Volkswagen, Skoda)
- Bikes / Motorcycles / Scooters (Royal Enfield, Honda, Yamaha, Bajaj, TVS, KTM, Hero, Suzuki)
- Cameras & Gaming Consoles (Sony, Canon, Nikon, Nintendo, Microsoft Xbox)

Used by:
1. Search & auto-complete API for dropdown selection in the app.
2. AI Item Identification pipeline to eliminate hallucinations and resolve ambiguous models.
3. Behavioral Risk & Price Anomaly model for realistic market valuation baselines.
4. Deterministic Device Mismatch detection between listing metadata and diagnostic hardware telemetry.
"""
from typing import Any, Dict, List, Optional
import re

CATALOG_ITEMS: List[Dict[str, Any]] = [
    # ══════════════════════════════════════════════════════════════════════════════
    # MOBILES — APPLE
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 16 Pro Max",
        "aliases": ["iphone 16 pro max", "16 pro max", "iphone17,2"],
        "hardware_ids": ["iphone17,2", "a3296", "a3084"],
        "typical_resale_price": {"min": 115000, "max": 140000, "currency": "INR"},
        "specs": {"screen": "6.9 inch OLED 120Hz", "chip": "Apple A18 Pro", "camera": "48MP + 48MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 16 Pro",
        "aliases": ["iphone 16 pro", "16 pro", "iphone17,1"],
        "hardware_ids": ["iphone17,1", "a3293", "a3083"],
        "typical_resale_price": {"min": 98000, "max": 120000, "currency": "INR"},
        "specs": {"screen": "6.3 inch OLED 120Hz", "chip": "Apple A18 Pro", "camera": "48MP + 48MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 16",
        "aliases": ["iphone 16", "16 standard", "iphone17,3"],
        "hardware_ids": ["iphone17,3", "a3287", "a3081"],
        "typical_resale_price": {"min": 65000, "max": 75000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED", "chip": "Apple A18", "camera": "48MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 15 Pro Max",
        "aliases": ["iphone 15 pro max", "15 pro max", "iphone16,2"],
        "hardware_ids": ["iphone16,2", "a3106", "a2849"],
        "typical_resale_price": {"min": 85000, "max": 105000, "currency": "INR"},
        "specs": {"screen": "6.7 inch OLED 120Hz", "chip": "Apple A17 Pro", "camera": "48MP + 12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 15 Pro",
        "aliases": ["iphone 15 pro", "15 pro", "iphone16,1"],
        "hardware_ids": ["iphone16,1", "a3102", "a2848"],
        "typical_resale_price": {"min": 75000, "max": 92000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED 120Hz", "chip": "Apple A17 Pro", "camera": "48MP + 12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 15",
        "aliases": ["iphone 15", "15 base", "iphone15,4"],
        "hardware_ids": ["iphone15,4", "a3090", "a2846"],
        "typical_resale_price": {"min": 50000, "max": 62000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED", "chip": "Apple A16 Bionic", "camera": "48MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 14 Pro Max",
        "aliases": ["iphone 14 pro max", "14 pro max", "iphone15,3"],
        "hardware_ids": ["iphone15,3", "a2894", "a2651"],
        "typical_resale_price": {"min": 68000, "max": 82000, "currency": "INR"},
        "specs": {"screen": "6.7 inch OLED 120Hz", "chip": "Apple A16 Bionic", "camera": "48MP + 12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 14 Pro",
        "aliases": ["iphone 14 pro", "14 pro", "iphone15,2"],
        "hardware_ids": ["iphone15,2", "a2890", "a2650"],
        "typical_resale_price": {"min": 58000, "max": 72000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED 120Hz", "chip": "Apple A16 Bionic", "camera": "48MP + 12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 14",
        "aliases": ["iphone 14", "14 base", "iphone14,7"],
        "hardware_ids": ["iphone14,7", "a2882", "a2649"],
        "typical_resale_price": {"min": 42000, "max": 52000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED", "chip": "Apple A15 Bionic", "camera": "12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 13 Pro Max",
        "aliases": ["iphone 13 pro max", "13 pro max", "iphone14,3"],
        "hardware_ids": ["iphone14,3", "a2643", "a2484"],
        "typical_resale_price": {"min": 52000, "max": 65000, "currency": "INR"},
        "specs": {"screen": "6.7 inch OLED 120Hz", "chip": "Apple A15 Bionic", "camera": "12MP + 12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 13",
        "aliases": ["iphone 13", "13 base", "iphone14,5"],
        "hardware_ids": ["iphone14,5", "a2633", "a2482"],
        "typical_resale_price": {"min": 35000, "max": 44000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED", "chip": "Apple A15 Bionic", "camera": "12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 12",
        "aliases": ["iphone 12", "12 base", "iphone13,2"],
        "hardware_ids": ["iphone13,2", "a2403", "a2172"],
        "typical_resale_price": {"min": 24000, "max": 32000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED", "chip": "Apple A14 Bionic", "camera": "12MP + 12MP"},
    },
    {
        "category": "mobile", "brand": "Apple", "model": "iPhone 11",
        "aliases": ["iphone 11", "11 base", "iphone12,1"],
        "hardware_ids": ["iphone12,1", "a2221", "a2111"],
        "typical_resale_price": {"min": 16000, "max": 22000, "currency": "INR"},
        "specs": {"screen": "6.1 inch Liquid Retina LCD", "chip": "Apple A13 Bionic", "camera": "12MP + 12MP"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # MOBILES — SAMSUNG
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy S24 Ultra",
        "aliases": ["samsung s24 ultra", "s24 ultra", "sm-s928", "galaxy s24 ultra"],
        "hardware_ids": ["sm-s928b", "sm-s928u", "sm-s928e", "sm-s9280", "sm-s928"],
        "typical_resale_price": {"min": 85000, "max": 105000, "currency": "INR"},
        "specs": {"screen": "6.8 inch Dynamic AMOLED 2X 120Hz", "chip": "Snapdragon 8 Gen 3", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy S24",
        "aliases": ["samsung s24", "s24", "sm-s921", "galaxy s24"],
        "hardware_ids": ["sm-s921b", "sm-s921u", "sm-s921e", "sm-s921"],
        "typical_resale_price": {"min": 52000, "max": 64000, "currency": "INR"},
        "specs": {"screen": "6.2 inch Dynamic AMOLED 2X", "chip": "Exynos 2400 / SD 8 Gen 3", "battery": "4000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy S23 Ultra",
        "aliases": ["samsung s23 ultra", "s23 ultra", "sm-s918", "galaxy s23 ultra"],
        "hardware_ids": ["sm-s918b", "sm-s918u", "sm-s918e", "sm-s918"],
        "typical_resale_price": {"min": 65000, "max": 78000, "currency": "INR"},
        "specs": {"screen": "6.8 inch Dynamic AMOLED 2X", "chip": "Snapdragon 8 Gen 2", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy S23",
        "aliases": ["samsung s23", "s23", "sm-s911", "galaxy s23"],
        "hardware_ids": ["sm-s911b", "sm-s911u", "sm-s911e", "sm-s911"],
        "typical_resale_price": {"min": 40000, "max": 48000, "currency": "INR"},
        "specs": {"screen": "6.1 inch Dynamic AMOLED 2X", "chip": "Snapdragon 8 Gen 2", "battery": "3900 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy S23 FE",
        "aliases": ["samsung s23 fe", "s23 fe", "sm-s711", "galaxy s23 fe"],
        "hardware_ids": ["sm-s711b", "sm-s711u", "sm-s711e", "sm-s711"],
        "typical_resale_price": {"min": 28000, "max": 35000, "currency": "INR"},
        "specs": {"screen": "6.4 inch Dynamic AMOLED 2X", "chip": "Exynos 2200 / SD 8 Gen 1", "battery": "4500 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy A35 5G",
        "aliases": ["samsung a35", "galaxy a35", "a35 5g", "sm-a356", "sm-a356e"],
        "hardware_ids": ["sm-a356e", "sm-a356b", "sm-a356u", "sm-a356"],
        "typical_resale_price": {"min": 18000, "max": 24000, "currency": "INR"},
        "specs": {"screen": "6.6 inch Super AMOLED 120Hz", "chip": "Exynos 1380", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy A55 5G",
        "aliases": ["samsung a55", "galaxy a55", "a55 5g", "sm-a556", "sm-a556e"],
        "hardware_ids": ["sm-a556e", "sm-a556b", "sm-a556u", "sm-a556"],
        "typical_resale_price": {"min": 24000, "max": 30000, "currency": "INR"},
        "specs": {"screen": "6.6 inch Super AMOLED 120Hz", "chip": "Exynos 1480", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy A15 5G",
        "aliases": ["samsung a15", "galaxy a15", "a15 5g", "sm-a156"],
        "hardware_ids": ["sm-a156e", "sm-a156b", "sm-a156u", "sm-a156"],
        "typical_resale_price": {"min": 11000, "max": 14500, "currency": "INR"},
        "specs": {"screen": "6.5 inch Super AMOLED 90Hz", "chip": "Dimensity 6100+", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy F15 5G",
        "aliases": ["samsung f15", "galaxy f15", "f15 5g", "sm-e156", "sm-f156"],
        "hardware_ids": ["sm-e156b", "sm-f156b", "sm-e156", "sm-f156"],
        "typical_resale_price": {"min": 9500, "max": 13500, "currency": "INR"},
        "specs": {"screen": "6.5 inch Super AMOLED 90Hz", "chip": "MediaTek Dimensity 6100+", "battery": "6000 mAh"},
    },
    {
        "category": "mobile", "brand": "Samsung", "model": "Galaxy M15 5G",
        "aliases": ["samsung m15", "galaxy m15", "m15 5g", "sm-m156"],
        "hardware_ids": ["sm-m156b", "sm-m156e", "sm-m156"],
        "typical_resale_price": {"min": 9000, "max": 13000, "currency": "INR"},
        "specs": {"screen": "6.5 inch Super AMOLED 90Hz", "chip": "MediaTek Dimensity 6100+", "battery": "6000 mAh"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # MOBILES — GOOGLE PIXEL & ONEPLUS
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "mobile", "brand": "Google", "model": "Pixel 8 Pro",
        "aliases": ["google pixel 8 pro", "pixel 8 pro", "husky"],
        "hardware_ids": ["pixel 8 pro", "gc3ve", "ge92"],
        "typical_resale_price": {"min": 55000, "max": 70000, "currency": "INR"},
        "specs": {"screen": "6.7 inch LTPO OLED 120Hz", "chip": "Google Tensor G3", "battery": "5050 mAh"},
    },
    {
        "category": "mobile", "brand": "Google", "model": "Pixel 8",
        "aliases": ["google pixel 8", "pixel 8", "shiba"],
        "hardware_ids": ["pixel 8", "g9b35", "gkws6"],
        "typical_resale_price": {"min": 42000, "max": 52000, "currency": "INR"},
        "specs": {"screen": "6.2 inch OLED 120Hz", "chip": "Google Tensor G3", "battery": "4575 mAh"},
    },
    {
        "category": "mobile", "brand": "Google", "model": "Pixel 7a",
        "aliases": ["google pixel 7a", "pixel 7a", "lynx"],
        "hardware_ids": ["pixel 7a", "gwk77", "ghl1x"],
        "typical_resale_price": {"min": 24000, "max": 30000, "currency": "INR"},
        "specs": {"screen": "6.1 inch OLED 90Hz", "chip": "Google Tensor G2", "battery": "4385 mAh"},
    },
    {
        "category": "mobile", "brand": "OnePlus", "model": "OnePlus 12",
        "aliases": ["oneplus 12", "op 12", "cph2581", "cph2573"],
        "hardware_ids": ["cph2581", "cph2573"],
        "typical_resale_price": {"min": 48000, "max": 58000, "currency": "INR"},
        "specs": {"screen": "6.82 inch LTPO AMOLED 120Hz", "chip": "Snapdragon 8 Gen 3", "battery": "5400 mAh"},
    },
    {
        "category": "mobile", "brand": "OnePlus", "model": "OnePlus 12R",
        "aliases": ["oneplus 12r", "op 12r", "cph2585"],
        "hardware_ids": ["cph2585", "cph2609"],
        "typical_resale_price": {"min": 30000, "max": 36000, "currency": "INR"},
        "specs": {"screen": "6.78 inch LTPO4 AMOLED 120Hz", "chip": "Snapdragon 8 Gen 2", "battery": "5500 mAh"},
    },
    {
        "category": "mobile", "brand": "OnePlus", "model": "OnePlus Nord CE 4",
        "aliases": ["nord ce 4", "oneplus nord ce 4", "cph2613"],
        "hardware_ids": ["cph2613"],
        "typical_resale_price": {"min": 17000, "max": 22000, "currency": "INR"},
        "specs": {"screen": "6.7 inch AMOLED 120Hz", "chip": "Snapdragon 7 Gen 3", "battery": "5500 mAh"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # MOBILES — XIAOMI / REDMI / REALME
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "mobile", "brand": "Xiaomi", "model": "Redmi Note 13 Pro+ 5G",
        "aliases": ["redmi note 13 pro+", "note 13 pro plus", "23090ra98g"],
        "hardware_ids": ["23090ra98g", "23090ra98i"],
        "typical_resale_price": {"min": 21000, "max": 26000, "currency": "INR"},
        "specs": {"screen": "6.67 inch AMOLED 120Hz", "chip": "Dimensity 7200-Ultra", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Xiaomi", "model": "Redmi Note 13 5G",
        "aliases": ["redmi note 13 5g", "note 13", "2312draf3g"],
        "hardware_ids": ["2312draf3g", "2312draf3i"],
        "typical_resale_price": {"min": 12000, "max": 15500, "currency": "INR"},
        "specs": {"screen": "6.67 inch AMOLED 120Hz", "chip": "Dimensity 6080", "battery": "5000 mAh"},
    },
    {
        "category": "mobile", "brand": "Realme", "model": "Realme 12 Pro+ 5G",
        "aliases": ["realme 12 pro+", "12 pro plus", "rmx3840"],
        "hardware_ids": ["rmx3840", "rmx3842"],
        "typical_resale_price": {"min": 20000, "max": 25000, "currency": "INR"},
        "specs": {"screen": "6.7 inch OLED 120Hz Curved", "chip": "Snapdragon 7s Gen 2", "camera": "64MP Periscope"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # LAPTOPS
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "laptop", "brand": "Apple", "model": "MacBook Air M1 (2020)",
        "aliases": ["macbook air m1", "macbookair10,1", "m1 air"],
        "hardware_ids": ["macbookair10,1", "a2337"],
        "typical_resale_price": {"min": 45000, "max": 56000, "currency": "INR"},
        "specs": {"screen": "13.3 inch Retina", "chip": "Apple M1", "ram": "8GB / 16GB", "storage": "256GB / 512GB"},
    },
    {
        "category": "laptop", "brand": "Apple", "model": "MacBook Air M2 (2022)",
        "aliases": ["macbook air m2", "macbookair14,2", "m2 air"],
        "hardware_ids": ["macbookair14,2", "a2681"],
        "typical_resale_price": {"min": 68000, "max": 82000, "currency": "INR"},
        "specs": {"screen": "13.6 inch Liquid Retina", "chip": "Apple M2", "ram": "8GB / 16GB", "storage": "256GB / 512GB"},
    },
    {
        "category": "laptop", "brand": "Apple", "model": "MacBook Pro 14 (M3, 2023)",
        "aliases": ["macbook pro 14 m3", "macbookpro15,3", "m3 pro"],
        "hardware_ids": ["macbookpro15,3", "a2918", "a2992"],
        "typical_resale_price": {"min": 120000, "max": 150000, "currency": "INR"},
        "specs": {"screen": "14.2 inch Liquid Retina XDR", "chip": "Apple M3 / M3 Pro", "storage": "512GB / 1TB"},
    },
    {
        "category": "laptop", "brand": "Dell", "model": "XPS 13",
        "aliases": ["dell xps 13", "xps 13 plus", "xps 9320", "xps 9315"],
        "hardware_ids": ["xps 9320", "xps 9315", "xps 9310"],
        "typical_resale_price": {"min": 65000, "max": 88000, "currency": "INR"},
        "specs": {"screen": "13.4 inch InfinityEdge FHD+ / 3.5K OLED", "chip": "Intel Core i7 12th/13th Gen"},
    },
    {
        "category": "laptop", "brand": "Dell", "model": "Inspiron 15 3520",
        "aliases": ["dell inspiron 15", "inspiron 3520", "inspiron 15"],
        "hardware_ids": ["inspiron 3520", "inspiron 3511"],
        "typical_resale_price": {"min": 26000, "max": 38000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD 120Hz", "chip": "Intel Core i3 / i5 12th Gen"},
    },
    {
        "category": "laptop", "brand": "HP", "model": "Pavilion 15",
        "aliases": ["hp pavilion 15", "pavilion 15-eg", "hp pavilion"],
        "hardware_ids": ["pavilion 15-eg", "pavilion 15-eh"],
        "typical_resale_price": {"min": 32000, "max": 45000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD IPS", "chip": "Intel Core i5 12th/13th Gen / Ryzen 5"},
    },
    {
        "category": "laptop", "brand": "Lenovo", "model": "ThinkPad T14 Gen 3",
        "aliases": ["thinkpad t14", "lenovo thinkpad t14", "t14 gen 3"],
        "hardware_ids": ["thinkpad t14 g3", "thinkpad 21ah"],
        "typical_resale_price": {"min": 45000, "max": 65000, "currency": "INR"},
        "specs": {"screen": "14 inch WUXGA IPS", "chip": "Intel Core i5 / i7 12th Gen", "build": "Mil-spec durability"},
    },
    {
        "category": "laptop", "brand": "Asus", "model": "TUF Gaming F15",
        "aliases": ["asus tuf f15", "tuf gaming f15", "fx506", "fx507"],
        "hardware_ids": ["fx506hc", "fx507zc", "fx507zv"],
        "typical_resale_price": {"min": 42000, "max": 58000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD 144Hz", "chip": "Intel Core i5 / i7", "gpu": "RTX 3050 / RTX 4050"},
    },
    {
        "category": "laptop", "brand": "Asus", "model": "ROG Zephyrus G14",
        "aliases": ["zephyrus g14", "asus rog g14", "ga402", "ga401"],
        "hardware_ids": ["ga402rj", "ga402rk", "ga403"],
        "typical_resale_price": {"min": 68000, "max": 95000, "currency": "INR"},
        "specs": {"screen": "14 inch QHD+ 120Hz/165Hz", "chip": "AMD Ryzen 7 / 9", "gpu": "RTX 4060 / Radeon RX"},
    },
    {
        "category": "laptop", "brand": "HP", "model": "Victus 15",
        "aliases": ["hp victus 15", "victus 15-fa", "victus 15-fb"],
        "hardware_ids": ["15-fa0000", "15-fb0000"],
        "typical_resale_price": {"min": 38000, "max": 52000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD 144Hz", "chip": "Intel Core i5 12th/13th Gen / Ryzen 5", "gpu": "RTX 2050 / 3050"},
    },
    {
        "category": "laptop", "brand": "HP", "model": "Omen 16",
        "aliases": ["hp omen 16", "omen 16-wd", "omen 16-xf"],
        "hardware_ids": ["16-wd0000", "16-xf0000"],
        "typical_resale_price": {"min": 65000, "max": 90000, "currency": "INR"},
        "specs": {"screen": "16.1 inch QHD 165Hz / 240Hz", "chip": "Intel Core i7 / Ryzen 7", "gpu": "RTX 4060 / 4070"},
    },
    {
        "category": "laptop", "brand": "Lenovo", "model": "IdeaPad Slim 3",
        "aliases": ["ideapad slim 3", "lenovo ideapad 3", "ideapad 15"],
        "hardware_ids": ["15iau7", "15aba7", "15iru8"],
        "typical_resale_price": {"min": 24000, "max": 35000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD", "chip": "Intel Core i3 / i5 / Ryzen 3 / 5", "ram": "8GB / 16GB"},
    },
    {
        "category": "laptop", "brand": "Lenovo", "model": "Legion 5",
        "aliases": ["lenovo legion 5", "legion 5 15", "legion 5 pro"],
        "hardware_ids": ["15iah7h", "15ach6h", "16irx8"],
        "typical_resale_price": {"min": 55000, "max": 78000, "currency": "INR"},
        "specs": {"screen": "15.6 inch / 16 inch 165Hz WQXGA", "chip": "AMD Ryzen 7 / Intel Core i7", "gpu": "RTX 3060 / RTX 4060"},
    },
    {
        "category": "laptop", "brand": "Acer", "model": "Nitro 5",
        "aliases": ["acer nitro 5", "nitro 5 an515", "an515-58"],
        "hardware_ids": ["an515-58", "an515-57", "an515-45"],
        "typical_resale_price": {"min": 38000, "max": 52000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD 144Hz", "chip": "Intel Core i5 12th Gen / Ryzen 5", "gpu": "RTX 3050"},
    },
    {
        "category": "laptop", "brand": "Acer", "model": "Aspire 5",
        "aliases": ["acer aspire 5", "aspire 5 a515"],
        "hardware_ids": ["a515-57", "a515-56"],
        "typical_resale_price": {"min": 22000, "max": 32000, "currency": "INR"},
        "specs": {"screen": "15.6 inch FHD IPS", "chip": "Intel Core i3 / i5 12th Gen", "ram": "8GB / 16GB"},
    },
    {
        "category": "laptop", "brand": "Microsoft", "model": "Surface Pro 9",
        "aliases": ["surface pro 9", "microsoft surface pro 9"],
        "hardware_ids": ["surface pro 9"],
        "typical_resale_price": {"min": 58000, "max": 80000, "currency": "INR"},
        "specs": {"screen": "13 inch PixelSense Flow 120Hz", "chip": "Intel Core i5 / i7 12th Gen / SQ3", "type": "2-in-1 Tablet Laptop"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # CARS — AUTOMOBILES
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "car", "brand": "Maruti Suzuki", "model": "Swift",
        "aliases": ["swift", "maruti swift", "swift vxi", "swift zxi", "swift lxi"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 380000, "max": 650000, "currency": "INR"},
        "specs": {"engine": "1.2L DualJet / Z-Series Petrol", "fuel": "Petrol / CNG", "mileage": "23-25 kmpl"},
    },
    {
        "category": "car", "brand": "Maruti Suzuki", "model": "Baleno",
        "aliases": ["baleno", "maruti baleno", "baleno delta", "baleno zeta", "baleno alpha"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 480000, "max": 750000, "currency": "INR"},
        "specs": {"engine": "1.2L K12N Petrol", "fuel": "Petrol / CNG", "mileage": "22.3 kmpl"},
    },
    {
        "category": "car", "brand": "Maruti Suzuki", "model": "Brezza",
        "aliases": ["brezza", "maruti brezza", "vitara brezza", "brezza vxi", "brezza zxi"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 650000, "max": 980000, "currency": "INR"},
        "specs": {"engine": "1.5L K15C Petrol", "body": "Compact SUV", "transmission": "Manual / 6AT"},
    },
    {
        "category": "car", "brand": "Hyundai", "model": "Creta",
        "aliases": ["creta", "hyundai creta", "creta sx", "creta ex", "creta sx(o)"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 850000, "max": 1450000, "currency": "INR"},
        "specs": {"engine": "1.5L MPi Petrol / 1.5L CRDi Diesel / 1.5L Turbo", "body": "Midsize SUV"},
    },
    {
        "category": "car", "brand": "Hyundai", "model": "Venue",
        "aliases": ["venue", "hyundai venue", "venue s+", "venue sx"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 600000, "max": 920000, "currency": "INR"},
        "specs": {"engine": "1.2L Petrol / 1.0L Turbo / 1.5L Diesel", "body": "Subcompact SUV"},
    },
    {
        "category": "car", "brand": "Hyundai", "model": "i20",
        "aliases": ["i20", "hyundai i20", "i20 sportz", "i20 asta", "i20 magna"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 450000, "max": 720000, "currency": "INR"},
        "specs": {"engine": "1.2L Kappa Petrol", "body": "Premium Hatchback"},
    },
    {
        "category": "car", "brand": "Tata", "model": "Nexon",
        "aliases": ["nexon", "tata nexon", "nexon creative", "nexon fearless", "nexon pure"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 680000, "max": 1150000, "currency": "INR"},
        "specs": {"engine": "1.2L Turbo Petrol / 1.5L Diesel / EV", "safety": "5-Star GNCAP / BNCAP"},
    },
    {
        "category": "car", "brand": "Tata", "model": "Punch",
        "aliases": ["punch", "tata punch", "punch adventure", "punch accomplished", "punch creative"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 520000, "max": 780000, "currency": "INR"},
        "specs": {"engine": "1.2L Revotron Petrol / iCNG / EV", "safety": "5-Star GNCAP"},
    },
    {
        "category": "car", "brand": "Tata", "model": "Harrier",
        "aliases": ["harrier", "tata harrier", "harrier adventure", "harrier fearless"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 1200000, "max": 1900000, "currency": "INR"},
        "specs": {"engine": "2.0L Kryotec Diesel", "safety": "5-Star BNCAP", "body": "Midsize SUV"},
    },
    {
        "category": "car", "brand": "Mahindra", "model": "Thar",
        "aliases": ["thar", "mahindra thar", "thar 4x4", "thar rwd", "thar lx"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 1050000, "max": 1550000, "currency": "INR"},
        "specs": {"engine": "2.0L mStallion Turbo / 2.2L mHawk Diesel", "drivetrain": "4x4 / RWD"},
    },
    {
        "category": "car", "brand": "Mahindra", "model": "Scorpio-N",
        "aliases": ["scorpio n", "scorpio-n", "mahindra scorpio n", "scorpio z8", "scorpio z4"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 1350000, "max": 2050000, "currency": "INR"},
        "specs": {"engine": "2.0L Turbo Petrol / 2.2L mHawk Diesel", "seating": "6/7 seater"},
    },
    {
        "category": "car", "brand": "Toyota", "model": "Fortuner",
        "aliases": ["fortuner", "toyota fortuner", "fortuner 4x4", "fortuner 4x2", "legender"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 2500000, "max": 4200000, "currency": "INR"},
        "specs": {"engine": "2.8L Diesel / 2.7L Petrol", "body": "Fullsize Ladder-frame SUV"},
    },
    {
        "category": "car", "brand": "Honda", "model": "City",
        "aliases": ["city", "honda city", "city 5th gen", "city v", "city vx", "city zx"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 750000, "max": 1250000, "currency": "INR"},
        "specs": {"engine": "1.5L i-VTEC Petrol / e:HEV Hybrid", "body": "Sedan"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # BIKES & SCOOTERS — TWO WHEELERS
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "bike", "brand": "Royal Enfield", "model": "Classic 350",
        "aliases": ["classic 350", "re classic 350", "bullet 350", "re classic"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 130000, "max": 185000, "currency": "INR"},
        "specs": {"engine": "349cc J-Series Single Cylinder", "power": "20.2 bhp", "brakes": "Dual Disc ABS"},
    },
    {
        "category": "bike", "brand": "Royal Enfield", "model": "Hunter 350",
        "aliases": ["hunter 350", "re hunter", "hunter metro", "hunter dapper"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 115000, "max": 150000, "currency": "INR"},
        "specs": {"engine": "349cc J-Series", "power": "20.2 bhp", "weight": "181 kg (roadster)"},
    },
    {
        "category": "bike", "brand": "Honda", "model": "Activa 6G",
        "aliases": ["activa 6g", "honda activa", "activa 110", "activa dlx"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 45000, "max": 68000, "currency": "INR"},
        "specs": {"engine": "109.5cc PGM-FI", "fuel": "Petrol", "type": "Scooter"},
    },
    {
        "category": "bike", "brand": "Yamaha", "model": "YZF R15 V4",
        "aliases": ["r15", "r15 v4", "yamaha r15", "r15m"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 125000, "max": 165000, "currency": "INR"},
        "specs": {"engine": "155cc Liquid Cooled VVA", "power": "18.4 bhp", "type": "Supersport"},
    },
    {
        "category": "bike", "brand": "Yamaha", "model": "MT-15 V2",
        "aliases": ["mt 15", "mt15", "yamaha mt 15", "mt-15"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 118000, "max": 150000, "currency": "INR"},
        "specs": {"engine": "155cc Liquid Cooled VVA", "power": "18.4 bhp", "type": "Street Naked"},
    },
    {
        "category": "bike", "brand": "Bajaj", "model": "Pulsar 150",
        "aliases": ["pulsar 150", "bajaj pulsar 150", "pulsar classic"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 48000, "max": 75000, "currency": "INR"},
        "specs": {"engine": "149.5cc DTS-i", "power": "14 bhp", "brakes": "Front Disc + ABS"},
    },
    {
        "category": "bike", "brand": "Bajaj", "model": "Pulsar NS200",
        "aliases": ["pulsar ns200", "ns200", "bajaj ns200"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 85000, "max": 120000, "currency": "INR"},
        "specs": {"engine": "199.5cc Triple Spark 4-Valve Liquid Cooled", "power": "24.5 bhp"},
    },
    {
        "category": "bike", "brand": "TVS", "model": "Apache RTR 160 4V",
        "aliases": ["apache 160 4v", "apache rtr 160", "tvs apache 160"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 75000, "max": 105000, "currency": "INR"},
        "specs": {"engine": "159.7cc Oil-Cooled 4V", "power": "17.55 bhp", "modes": "Urban / Rain / Sport"},
    },
    {
        "category": "bike", "brand": "TVS", "model": "Jupiter 110",
        "aliases": ["jupiter", "tvs jupiter", "jupiter zx", "jupiter classic"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 40000, "max": 62000, "currency": "INR"},
        "specs": {"engine": "109.7cc CVTi", "fuel": "Petrol", "type": "Scooter"},
    },
    {
        "category": "bike", "brand": "KTM", "model": "Duke 200",
        "aliases": ["ktm duke 200", "duke 200", "ktm 200"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 110000, "max": 155000, "currency": "INR"},
        "specs": {"engine": "199.5cc Liquid Cooled DOHC", "power": "25 bhp", "brakes": "Dual Channel ABS"},
    },
    {
        "category": "bike", "brand": "KTM", "model": "Duke 390",
        "aliases": ["ktm duke 390", "duke 390", "ktm 390"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 175000, "max": 250000, "currency": "INR"},
        "specs": {"engine": "398.7cc LC4c Engine", "power": "45 bhp", "electronics": "Quickshifter + Cornering ABS"},
    },

    # ══════════════════════════════════════════════════════════════════════════════
    # CAMERAS & GAMING
    # ══════════════════════════════════════════════════════════════════════════════
    {
        "category": "gaming", "brand": "Sony", "model": "PlayStation 5 (Disc Edition)",
        "aliases": ["ps5 disc", "playstation 5 disc", "ps5", "cfi-1208a"],
        "hardware_ids": ["cfi-1000", "cfi-1100", "cfi-1200", "cfi-2000"],
        "typical_resale_price": {"min": 35000, "max": 42000, "currency": "INR"},
        "specs": {"storage": "825GB / 1TB SSD", "graphics": "Custom RDNA 2 10.3 TFLOPS", "resolution": "4K 120Hz"},
    },
    {
        "category": "gaming", "brand": "Nintendo", "model": "Switch OLED",
        "aliases": ["nintendo switch oled", "switch oled", "heg-001"],
        "hardware_ids": ["heg-001"],
        "typical_resale_price": {"min": 20000, "max": 25000, "currency": "INR"},
        "specs": {"screen": "7.0 inch OLED", "storage": "64GB", "type": "Hybrid Console"},
    },
    {
        "category": "camera", "brand": "Sony", "model": "Alpha A7 III",
        "aliases": ["sony a7 iii", "a7m3", "ilce-7m3", "a7 iii"],
        "hardware_ids": ["ilce-7m3"],
        "typical_resale_price": {"min": 85000, "max": 110000, "currency": "INR"},
        "specs": {"sensor": "24.2MP Full-Frame BSI CMOS", "video": "4K HDR", "stabilization": "5-axis In-body"},
    },
    {
        "category": "camera", "brand": "Canon", "model": "EOS R6",
        "aliases": ["canon eos r6", "canon r6", "eos r6"],
        "hardware_ids": [],
        "typical_resale_price": {"min": 115000, "max": 145000, "currency": "INR"},
        "specs": {"sensor": "20.1MP Full-Frame CMOS", "video": "4K 60p 10-bit 4:2:2", "stabilization": "8-stop IBIS"},
    },
]


def search_catalog(query: str, category: Optional[str] = None, limit: int = 15) -> List[Dict[str, Any]]:
    """Search catalog items by query string across brand, model, and aliases."""
    if not query:
        # If query is empty, return popular items in that category
        res = [it for it in CATALOG_ITEMS if not category or it["category"] == category]
        return res[:limit]

    q_clean = query.strip().lower()
    terms = q_clean.split()

    scored_results = []
    for it in CATALOG_ITEMS:
        if category and it["category"] != category:
            continue

        brand_lower = it["brand"].lower()
        model_lower = it["model"].lower()
        aliases = [a.lower() for a in it.get("aliases", [])]
        hw_ids = [h.lower() for h in it.get("hardware_ids", [])]

        full_text = f"{brand_lower} {model_lower} {' '.join(aliases)} {' '.join(hw_ids)}"

        score = 0
        # Exact model match
        if q_clean == model_lower or q_clean in aliases:
            score += 100
        # Prefix match on model or brand
        elif model_lower.startswith(q_clean) or brand_lower.startswith(q_clean):
            score += 70
        # Substring in model
        elif q_clean in model_lower:
            score += 50
        # Substring in brand
        elif q_clean in brand_lower:
            score += 40
        # Substring in aliases or hardware ids
        elif any(q_clean in a for a in aliases) or any(q_clean in h for h in hw_ids):
            score += 35
        # All individual words match somewhere
        elif all(t in full_text for t in terms):
            score += 25

        if score > 0:
            scored_results.append((score, it))

    # Sort descending by score
    scored_results.sort(key=lambda s: s[0], reverse=True)
    results = [it for _, it in scored_results[:limit]]

    # If category is mobile/laptop and results are fewer than limit, query MobileAPI.dev
    if (not category or category in ("mobile", "laptop")) and len(results) < limit and len(q_clean) >= 2:
        try:
            api_devices = fetch_mobileapi_devices(q_clean, limit=limit - len(results))
            existing_names = {it["model"].lower() for it in results}
            for d in api_devices:
                if d["model"].lower() not in existing_names:
                    results.append(d)
                    existing_names.add(d["model"].lower())
        except Exception:
            pass

    return results[:limit]


def fetch_mobileapi_devices(query: str, limit: int = 15) -> List[Dict[str, Any]]:
    """Fetch devices from MobileAPI.dev using configured Bearer token."""
    try:
        from app.core.config import settings
        key = getattr(settings, "mobileapi_key", "").strip()
        if not key:
            return []
        import urllib.request, urllib.parse, json
        url = f"https://api.mobileapi.dev/devices/search/?name={urllib.parse.quote(query)}"
        req = urllib.request.Request(
            url,
            headers={
                "Authorization": f"Bearer {key}",
                "User-Agent": "SafeResale/1.0",
                "Accept": "application/json"
            }
        )
        with urllib.request.urlopen(req, timeout=4) as response:
            if response.status == 200:
                data = json.loads(response.read().decode())
                devices = data.get("devices", [])
                out = []
                for d in devices[:limit]:
                    brand = (d.get("manufacturer_name") or "").capitalize()
                    name = d.get("name") or ""
                    model_numbers_str = d.get("model_numbers") or ""
                    hw_ids = [m.strip().lower() for m in model_numbers_str.split(",") if m.strip()]
                    specs = {}
                    if d.get("storage"):
                        specs["storage"] = d.get("storage")
                    if d.get("screen_resolution"):
                        specs["screen"] = d.get("screen_resolution")
                    if d.get("colors"):
                        specs["colors"] = d.get("colors")
                    if d.get("device_type"):
                        specs["type"] = d.get("device_type")

                    cat = "mobile"
                    dtype = (d.get("device_type") or "").lower()
                    if "laptop" in dtype:
                        cat = "laptop"
                    elif "tablet" in dtype:
                        cat = "mobile"

                    out.append({
                        "category": cat,
                        "brand": brand,
                        "model": name,
                        "aliases": [name.lower()] + hw_ids,
                        "hardware_ids": hw_ids,
                        "typical_resale_price": None,
                        "specs": specs,
                        "source": "mobileapi_dev",
                        "mobileapi_id": d.get("id"),
                    })
                return out
    except Exception:
        pass
    return []


def get_brands_for_category(category: str) -> List[str]:
    """Return distinct sorted brands for a category."""
    brands = {it["brand"] for it in CATALOG_ITEMS if it["category"] == category}
    return sorted(brands)


def get_models_for_brand(category: str, brand: str) -> List[Dict[str, Any]]:
    """Return all models for a specific brand and category.
    
    If the brand is in car/bike and has few local models, dynamically enriches
    from the free public NHTSA vPIC database.
    """
    b_lower = brand.strip().lower()
    local = [
        it for it in CATALOG_ITEMS
        if it["category"] == category and it["brand"].lower() == b_lower
    ]
    if len(local) >= 10:
        return local

    # Dynamic enrichment from MobileAPI.dev for mobiles & laptops
    if category in ("mobile", "laptop"):
        try:
            api_devices = fetch_mobileapi_devices(brand, limit=25)
            existing_models = {it["model"].lower() for it in local}
            enriched = list(local)
            for d in api_devices:
                if d["model"].lower() not in existing_models and (not d.get("brand") or d.get("brand").lower() == b_lower):
                    enriched.append(d)
                    existing_models.add(d["model"].lower())
            return enriched
        except Exception:
            pass

    # Dynamic enrichment from free NHTSA vPIC API for automotive and two-wheelers
    if category in ("car", "bike", "vehicle"):
        try:
            import urllib.request, urllib.parse, json
            clean_brand = brand.replace("Maruti Suzuki", "Suzuki").replace("Royal Enfield", "Royal Enfield").strip()
            url = f"https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMake/{urllib.parse.quote(clean_brand)}?format=json"
            req = urllib.request.Request(url, headers={"User-Agent": "SafeResale/1.0"})
            with urllib.request.urlopen(req, timeout=3) as r:
                data = json.loads(r.read())
                nhtsa_names = sorted({item["Model_Name"].strip() for item in data.get("Results", []) if item.get("Model_Name")})
                existing_models = {it["model"].lower() for it in local}
                enriched = list(local)
                for m in nhtsa_names:
                    if m.lower() not in existing_models and len(enriched) < 40:
                        enriched.append({
                            "category": category,
                            "brand": brand.title(),
                            "model": m,
                            "aliases": [m.lower()],
                            "hardware_ids": [],
                            "typical_resale_price": None,
                            "source": "nhtsa_vpic_api",
                        })
                return enriched
        except Exception:
            pass
    return local


def find_by_hardware_id(hw_id: str) -> Optional[Dict[str, Any]]:
    """Look up canonical catalog item by hardware identifier (e.g. 'sm-a356e', 'iphone14,5')."""
    if not hw_id:
        return None
    cleaned = hw_id.strip().lower()
    for it in CATALOG_ITEMS:
        for hid in it.get("hardware_ids", []):
            if hid in cleaned or cleaned in hid:
                return it
        for alias in it.get("aliases", []):
            if alias in cleaned:
                return it
    return None


def get_category_summary() -> List[Dict[str, Any]]:
    """Return summary of all categories and their top brands."""
    cats = ["mobile", "laptop", "car", "bike", "gaming", "camera"]
    out = []
    for c in cats:
        brands = get_brands_for_category(c)
        items_count = len([it for it in CATALOG_ITEMS if it["category"] == c])
        out.append({
            "category": c,
            "total_models": items_count,
            "brands": brands,
        })
    return out
