package com.example.hldsn.services.news;

import com.example.hldsn.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DummyNewsRepository implements NewsRepository {

    @Override
    public void fetchNews(boolean forceRefresh, Callback callback) {
        callback.onSuccess(buildNews());
    }

    private List<NewsItem> buildNews() {
        List<NewsItem> items = new ArrayList<>();

        items.add(new NewsItem(
                "Monsoon Flooding Displaces Families in Southern Sindh",
                "Heavy monsoon rain has flooded low-lying villages near Badin, with rescue teams shifting families to temporary camps.",
                "Continuous monsoon rainfall has submerged multiple low-lying settlements in Badin and adjoining districts. "
                        + "District administration teams, with support from local rescue volunteers, have moved families to temporary shelters in school buildings and community halls. "
                        + "Relief officials report that clean drinking water, dry ration packs, and medical support remain the top priorities as standing water continues to rise in vulnerable villages.",
                "Badin, Sindh, Pakistan",
                "April 24, 2026",
                R.drawable.flood_banner
        ));

        items.add(new NewsItem(
                "Strong Earthquake Tremors Felt Across Northern Pakistan",
                "Residents in KP and parts of Gilgit-Baltistan reported intense shaking. Local authorities are checking remote valleys for damage.",
                "A moderate-to-strong seismic event triggered tremors across major parts of Khyber Pakhtunkhwa and Gilgit-Baltistan. "
                        + "Emergency response desks in district headquarters have started collecting field updates from mountain communities where aftershocks may cause landslides. "
                        + "So far, authorities have urged residents to stay alert, avoid damaged structures, and follow official advisories while structural inspections are underway.",
                "KP and Gilgit-Baltistan, Pakistan",
                "April 24, 2026",
                R.drawable.image_3
        ));

        items.add(new NewsItem(
                "Landslide Blocks Karakoram Highway After Night Rain",
                "A fresh landslide near Diamer has blocked traffic and delayed relief supplies. Clearance work is underway with heavy machinery.",
                "Overnight rain triggered a landslide near a critical stretch of the Karakoram Highway in Diamer district, disrupting passenger movement and supply transport. "
                        + "National and local highway teams have mobilized excavators to clear debris while traffic police redirect vehicles to temporary holding areas. "
                        + "Officials expect phased reopening after slope safety assessment, with priority movement likely for emergency and relief vehicles.",
                "Diamer, Gilgit-Baltistan, Pakistan",
                "April 23, 2026",
                R.drawable.ic_disaster
        ));

        items.add(new NewsItem(
                "Flash Flood Warning Issued for Hill Torrents in Balochistan",
                "PDMA has warned communities near seasonal streams to avoid crossings as sudden runoff is expected over the next 24 hours.",
                "PDMA Balochistan has issued a precautionary flash-flood advisory for hill torrent zones after weather models indicated intense localized rain. "
                        + "Communities living close to seasonal nullahs have been directed to avoid crossings, move livestock to safer ground, and coordinate with local disaster focal persons. "
                        + "District control rooms remain on standby to support evacuations if water levels rise rapidly.",
                "Kalat and Khuzdar belt, Balochistan, Pakistan",
                "April 22, 2026",
                R.drawable.flood_banner
        ));

        items.add(new NewsItem(
                "Heatwave Emergency Alert for Central Punjab Cities",
                "Health officials have advised reduced daytime exposure as temperatures are forecast to remain dangerously high this week.",
                "Provincial health authorities have activated a heatwave advisory for central Punjab as daytime temperatures are projected to remain above seasonal norms. "
                        + "Hospitals have been instructed to prepare for dehydration and heatstroke cases, especially among elderly citizens, children, and outdoor workers. "
                        + "Residents are advised to stay hydrated, avoid direct sun during peak afternoon hours, and seek immediate medical assistance for heat exhaustion symptoms.",
                "Lahore, Faisalabad, and Multan, Punjab, Pakistan",
                "April 24, 2026",
                R.drawable.ic_disaster
        ));

        Collections.shuffle(items);
        return items;
    }
}

