package corsica.universita.journalvoyage.data

/**
 * Le contenu statique de la PWA (index.html) : la section « Destinations inspirantes »
 * et l'itinéraire de démonstration. Mêmes textes, mêmes images (assets/seed/).
 */
data class Destination(val nom: String, val etapeTitre: String, val lieu: String, val description: String, val image: String)

object Destinations {
    val all = listOf(
        Destination(
            "Aiguilles de Bavella", "Aiguilles de Bavella", "Corse, France",
            "Randonnée spectaculaire à travers les aiguilles de granite rouge. Un panorama à couper le souffle sur les montagnes corses et la mer Tyrrhénienne.",
            "seed/bavella.jpg",
        ),
        Destination(
            "Calanques de Piana", "Calanques de Piana", "Corse, France",
            "Classées au patrimoine mondial de l'UNESCO, ces formations rocheuses de granit rose plongent dans une mer turquoise. Un spectacle naturel unique en Méditerranée.",
            "seed/piana.jpg",
        ),
        Destination(
            "Palombaggia", "Plage de Palombaggia", "Porto-Vecchio, Corse",
            "Sable fin, eaux cristallines et pins parasols : l'une des plus belles plages de France. Idéale pour une journée de détente entre deux randonnées.",
            "seed/palombaggia.jpg",
        ),
    )
}
