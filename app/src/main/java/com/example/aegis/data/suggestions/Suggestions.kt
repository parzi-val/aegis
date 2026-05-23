package com.example.aegis.data.suggestions

object Suggestions {

    val conditions: List<String> = listOf(
        "ADHD", "Alzheimer's Disease", "Anaemia", "Anxiety Disorder", "Appendicitis",
        "Asthma", "Atrial Fibrillation", "Autism Spectrum Disorder", "Benign Prostatic Hyperplasia",
        "Bipolar Disorder", "Bronchitis", "COPD", "Cardiomyopathy", "Cataract",
        "Celiac Disease", "Cervical Spondylosis", "Chronic Fatigue Syndrome", "Chronic Kidney Disease",
        "Coronary Artery Disease", "Crohn's Disease", "Deep Vein Thrombosis", "Dementia",
        "Depression", "Dengue Fever", "Diabetes Type 1", "Diabetes Type 2", "Diabetic Neuropathy",
        "Diabetic Retinopathy", "Eczema", "Endometriosis", "Epilepsy", "Fatty Liver Disease (NAFLD)",
        "Fibromyalgia", "Gallstones", "GERD (Acid Reflux)", "Glaucoma", "Gout",
        "Heart Failure", "Hearing Loss", "Hepatitis B", "Hepatitis C", "Hernia",
        "HIV / AIDS", "Hypertension", "Hyperlipidaemia", "Hyperthyroidism", "Hypothyroidism",
        "IBS (Irritable Bowel Syndrome)", "Iron Deficiency Anaemia", "Kidney Stones", "Liver Cirrhosis",
        "Lupus", "Malaria", "Metabolic Syndrome", "Migraine", "Multiple Sclerosis",
        "Obesity", "Osteoarthritis", "Osteoporosis", "Pancreatitis", "Parkinson's Disease",
        "PCOS (Polycystic Ovary Syndrome)", "Peptic Ulcer", "Peripheral Artery Disease", "Psoriasis",
        "Rheumatoid Arthritis", "Schizophrenia", "Sickle Cell Disease", "Sleep Apnea",
        "Stroke", "Thalassemia", "Thyroid Nodule", "Tuberculosis (TB)", "Typhoid",
        "Ulcerative Colitis", "UTI (Urinary Tract Infection)", "Varicose Veins",
        "Vertigo", "Vitamin B12 Deficiency", "Vitamin D Deficiency",
    ).sorted()

    val medications: List<String> = listOf(
        // Antidiabetics
        "Metformin", "Glibenclamide", "Glimepiride", "Glipizide", "Pioglitazone",
        "Sitagliptin", "Vildagliptin", "Empagliflozin", "Dapagliflozin",
        "Insulin (Regular)", "Insulin (NPH)", "Insulin Glargine", "Insulin Aspart",
        // Antihypertensives
        "Amlodipine", "Telmisartan", "Losartan", "Olmesartan", "Enalapril",
        "Ramipril", "Lisinopril", "Perindopril", "Atenolol", "Metoprolol",
        "Bisoprolol", "Carvedilol", "Nebivolol", "Hydrochlorothiazide",
        "Furosemide", "Indapamide", "Spironolactone", "Clonidine",
        // Lipids
        "Atorvastatin", "Rosuvastatin", "Simvastatin", "Fenofibrate", "Ezetimibe",
        // Cardiac / Anticoagulants
        "Aspirin", "Clopidogrel", "Ticagrelor", "Warfarin", "Rivaroxaban",
        "Apixaban", "Dabigatran", "Digoxin", "Nitroglycerin", "Isosorbide Mononitrate",
        "Amiodarone", "Ivabradine",
        // Thyroid
        "Levothyroxine", "Carbimazole", "Propylthiouracil",
        // GI
        "Omeprazole", "Pantoprazole", "Rabeprazole", "Esomeprazole",
        "Domperidone", "Ondansetron", "Metoclopramide", "Loperamide",
        "Mesalazine", "Sucralfate",
        // Pain / NSAIDs
        "Paracetamol", "Ibuprofen", "Diclofenac", "Naproxen", "Aceclofenac",
        "Etoricoxib", "Mefenamic Acid", "Tramadol", "Codeine", "Morphine",
        "Gabapentin", "Pregabalin",
        // Psychotropics
        "Amitriptyline", "Escitalopram", "Sertraline", "Fluoxetine", "Paroxetine",
        "Venlafaxine", "Duloxetine", "Mirtazapine", "Clonazepam", "Diazepam",
        "Alprazolam", "Haloperidol", "Risperidone", "Olanzapine", "Quetiapine",
        "Aripiprazole", "Lithium", "Valproate", "Carbamazepine", "Levetiracetam",
        "Lamotrigine", "Phenytoin",
        // Respiratory
        "Salbutamol", "Formoterol", "Salmeterol", "Budesonide", "Fluticasone",
        "Tiotropium", "Montelukast", "Theophylline",
        // Antihistamines
        "Cetirizine", "Loratadine", "Fexofenadine", "Levocetirizine", "Hydroxyzine",
        // Antibiotics (common/chronic use)
        "Amoxicillin", "Amoxicillin-Clavulanate", "Azithromycin", "Doxycycline",
        "Ciprofloxacin", "Metronidazole", "Trimethoprim-Sulfamethoxazole",
        // Steroids / DMARDs
        "Prednisolone", "Dexamethasone", "Methylprednisolone",
        "Hydroxychloroquine", "Methotrexate", "Sulfasalazine", "Leflunomide",
        // Bone / Supplements
        "Alendronate", "Calcium + Vitamin D3", "Vitamin D3", "Vitamin B12",
        "Folic Acid", "Ferrous Sulfate", "Iron + Folic Acid", "Zinc",
        // Urology
        "Tamsulosin", "Finasteride", "Sildenafil", "Tadalafil",
    ).sorted()

    val allergens: List<String> = listOf(
        // Drug allergens
        "Aspirin", "Amoxicillin", "Ampicillin", "Cephalosporins", "Codeine",
        "Contrast Dye (Iodine)", "Ibuprofen / NSAIDs", "Metronidazole",
        "Morphine", "Penicillin", "Quinolone Antibiotics", "Sulfonamides (Sulfa drugs)",
        "Tetracyclines", "Tramadol",
        // Environmental
        "Bee Sting", "Cat Dander", "Cockroach", "Dog Dander",
        "Grass Pollen", "House Dust Mites", "Insect Bites", "Mould",
        "Tree Pollen", "Wasp Sting",
        // Food
        "Almond", "Cashew", "Eggs", "Fish", "Groundnuts / Peanuts",
        "Milk (Dairy)", "Mustard", "Sesame", "Shellfish", "Shrimp / Prawns",
        "Soy", "Tree Nuts", "Walnut", "Wheat / Gluten",
        // Contact / Chemical
        "Food Colouring", "Formaldehyde", "Fragrance / Perfume", "Latex",
        "MSG (Monosodium Glutamate)", "Nickel", "Parabens", "Preservatives",
        "Sulphites",
    ).sorted()
}
