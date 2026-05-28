package dev.vinciguerra.adrsentinel.db.onunumber;

/**
 * Enumerazione di dominio (Domain Enum) che definisce le macro-categorie fisiche 
 * relative alla modalità di imballaggio e trasporto delle merci pericolose (ADR).
 * <p>
 * Nel contesto dell'architettura Domain-Driven Design (DDD) di AdrSentinel, 
 * questa enumerazione rappresenta un asse decisionale critico. La modalità di 
 * trasporto non altera la natura chimica della sostanza (definita dal Numero ONU), 
 * ma ne stravolge completamente i requisiti logistici e normativi.
 * </p>
 * <p>
 * <b>Impatto sull'Algoritmo di Matchmaking:</b><br>
 * Il motore di ottimizzazione utilizza questa costante per incrociare la richiesta 
 * del cliente con le caratteristiche della flotta aziendale:
 * <ul>
 * <li><b>Vincoli Strutturali:</b> Un trasporto in {@code TANK} esige fisicamente un'autobotte 
 * (es. {@code VehicleType.TANKER}) o un telaio per Isotank, rendendo inutilizzabili i normali furgoni.</li>
 * <li><b>Vincoli Normativi:</b> Trasportare liquidi infiammabili in {@code TANK} fa scattare 
 * l'obbligo inderogabile di omologazione {@code FL} per il veicolo. Lo stesso obbligo decade 
 * se l'identica merce viaggia in {@code PACKAGES}.</li>
 * </ul>
 * </p>
 * @author GiovanniVinciguerra
 * @version 1.0
 * @since 3.0
 */
public enum TransportMode {
	/**
     * <b>Trasporto in Colli (Packages)</b>
     * <p>
     * La merce è contenuta in imballaggi indipendenti e omologati prima di essere caricata 
     * sul veicolo. Questa modalità include scatole, fusti, taniche, cilindri (bombole) 
     * e cisternette IBC (Intermediate Bulk Containers) posizionate su pallet.
     * </p>
     * <p>
     * <b>Regole di Dominio:</b><br>
     * Di norma, il trasporto in colli garantisce la massima flessibilità logistica. 
     * Non richiede l'omologazione speciale del veicolo con la barratura rosa 
     * (come i certificati FL o AT), a eccezione della Classe 1 (Esplosivi) e Classe 7 (Radioattivi). 
     * È la configurazione standard per l'assegnazione a classici automezzi centinati/telonati 
     * ({@code CURTAINSIDE}), furgoni ({@code VAN}) o cassonati.
     * </p>
     */
    PACKAGES,
    /**
     * <b>Trasporto in Cisterna (Tank)</b>
     * <p>
     * Materia liquida, gassosa o polverulenta trasportata allo stato sfuso all'interno 
     * di un serbatoio permanentemente fissato al telaio (autobotte) o in una cisterna mobile.
     * </p>
     * <p>
     * <b>Regole di Dominio:</b><br>
     * Rappresenta lo scenario normativo e di sicurezza stradale più severo. 
     * L'algoritmo di dispatching deve tassativamente abbinare questa modalità a veicoli 
     * strutturalmente idonei e deve verificare in modo rigoroso la presenza del certificato 
     * di approvazione ADR appropriato per la classe di pericolo trasportata 
     * (es. omologazione {@code FL} per gli infiammabili, {@code AT} per le altre classi).
     * </p>
     */
    TANK,
    /**
     * <b>Trasporto alla Rinfusa (Bulk)</b>
     * <p>
     * Materia esclusivamente solida (es. terre contaminate, carbone, granulati chimici) 
     * caricata e contenuta direttamente nel vano di carico del veicolo, senza alcun 
     * imballaggio intermedio.
     * </p>
     * <p>
     * <b>Regole di Dominio:</b><br>
     * Questa modalità restringe drasticamente il bacino dei veicoli compatibili. 
     * L'algoritmo deve escludere le cisterne e i furgoni, ricercando mezzi strutturati 
     * per il contenimento di solidi sfusi (tipicamente veicoli ribaltabili - {@code TIPPER}) 
     * o pianali specificatamente attrezzati. A livello normativo, l'entità ONU associata 
     * dovrà possedere i codici autorizzativi (es. BK1, BK2, VC) che consentono espressamente 
     * il trasporto alla rinfusa per quella specifica materia.
     * </p>
     */
    BULK
}
