package mg.itu.etu004361;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {

    // le nom de la vue (ex: "employe/liste")
    // le framework ajoutera préfixe et suffixe autour
    private String url;

    // les données à passer à la JSP
    // clé   = le nom qu'on utilisera dans la JSP  (ex: "employes")
    // valeur = l'objet réel                        (ex: une liste d'employés)
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

    /*
     * setAttribute : ajoute une donnée dans la map
     * ex: mv.setAttribute("employes", maListe)
     * -> data.put("employes", maListe)
     */
    public void setAttribute(String nom, Object valeur) {
        this.data.put(nom, valeur);
    }

    // getter pour récupérer toute la map (utilisé par FrontServlet)
    public Map<String, Object> getData() {
        return data;
    }
}
