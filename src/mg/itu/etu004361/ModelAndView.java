package mg.itu.etu004361;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {
    private String url;
    private Map<String, Object> data;

    // constructeur : on initialise la map vide
    public ModelAndView() {
        this.data = new HashMap<>();
    }

    // setter pour l'url
    public void setUrl(String url) {
        this.url = url;
    }

    // getter pour l'url
    public String getUrl() {
        return url;
    }

    /* setAttribute : ajoute une donnee dans la map */
    public void setAttribute(String nom, Object valeur) {
        this.data.put(nom, valeur);
    }

    // getter pour recuperer toute la map (utilise par FrontServlet)
    public Map<String, Object> getData() {
        return data;
    }
}
