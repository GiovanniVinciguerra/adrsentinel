package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta della logica di validazione per l'annotazione custom {@link ValidatorLicense}.
 * <p>Questa classe definisce il motore di validazione formale per i numeri di patente di guida 
 * rilasciati negli Stati Membri dell'Unione Europea e in alcuni Paesi extra-UE (come Regno Unito, 
 * Svizzera, Norvegia e Islanda).</p>
 * <p><b>Strategia di Validazione:</b></p>
 * <p>Il validatore adotta un approccio "agnostico" rispetto alla nazione: non richiede che il client 
 * specifichi il Paese di emissione. La stringa in input viene considerata valida se, dopo un processo 
 * di igienizzazione (sanitization), risulta conforme ad <b>almeno uno</b> dei pattern Regex definiti 
 * nel dizionario interno.</p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLicense
 */
public class LicenseValidator implements ConstraintValidator<ValidatorLicense, String> {
	/**
     * Dizionario in memoria precompilato contenente le espressioni regolari (Regex) associate 
     * ai formati delle patenti di guida nazionali.
     * <p>L'uso di {@link Pattern#compile(String)} all'interno di un blocco statico garantisce 
     * che le espressioni vengano compilate una sola volta al caricamento della classe, 
     * ottimizzando le performance durante le richieste concorrenti.</p>
     */
	private static final Map<String, Pattern> LICENSE_PATTERNS = new HashMap<String, Pattern>();

    static {
        // --- STATI MEMBRI DELL'UNIONE EUROPEA ---
        // Austria (AT): 8 cifre
        LICENSE_PATTERNS.put("AT", Pattern.compile("^\\d{8}$"));
        // Belgio (BE): 10 cifre
        LICENSE_PATTERNS.put("BE", Pattern.compile("^\\d{10}$"));
        // Bulgaria (BG): 9 cifre
        LICENSE_PATTERNS.put("BG", Pattern.compile("^\\d{9}$"));
        // Cipro (CY): 12 caratteri alfanumerici
        LICENSE_PATTERNS.put("CY", Pattern.compile("^[A-Z0-9]{12}$"));
        // Croazia (HR): 8 cifre
        LICENSE_PATTERNS.put("HR", Pattern.compile("^\\d{8}$"));
        // Danimarca (DK): 8 cifre
        LICENSE_PATTERNS.put("DK", Pattern.compile("^\\d{8}$"));
        // Estonia (EE): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("EE", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Finlandia (FI): 6 cifre + 4 caratteri alfanumerici (basato sull'ID nazionale)
        LICENSE_PATTERNS.put("FI", Pattern.compile("^\\d{6}[A-Z0-9]{4}$"));
        // Francia (FR): Formato molto flessibile tra vecchio e nuovo registro (da 1 a 15 alfanumerici)
        LICENSE_PATTERNS.put("FR", Pattern.compile("^[A-Z0-9]{1,15}$"));
        // Germania (DE): 11 caratteri alfanumerici (Fahrerlaubnisnummer)
        LICENSE_PATTERNS.put("DE", Pattern.compile("^[A-Z0-9]{11}$"));
        // Grecia (GR): 9 cifre
        LICENSE_PATTERNS.put("GR", Pattern.compile("^\\d{9}$"));
        // Irlanda (IE): 9 caratteri alfanumerici
        LICENSE_PATTERNS.put("IE", Pattern.compile("^[A-Z0-9]{9}$"));
        // Italia (IT): 1 o 2 lettere + 7 cifre + 1 lettera finale
        LICENSE_PATTERNS.put("IT", Pattern.compile("^[A-Z]{1,2}\\d{7}[A-Z]$"));
        // Lettonia (LV): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("LV", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Lituania (LT): 8 cifre
        LICENSE_PATTERNS.put("LT", Pattern.compile("^\\d{8}$"));
        // Lussemburgo (LU): 6 cifre
        LICENSE_PATTERNS.put("LU", Pattern.compile("^\\d{6}$"));
        // Malta (MT): Da 6 a 8 caratteri alfanumerici
        LICENSE_PATTERNS.put("MT", Pattern.compile("^[A-Z0-9]{6,8}$"));
        // Paesi Bassi (NL): 10 cifre
        LICENSE_PATTERNS.put("NL", Pattern.compile("^\\d{10}$"));
        // Polonia (PL): Da 7 a 12 alfanumerici (ripulito dallo slash "/" nativo)
        LICENSE_PATTERNS.put("PL", Pattern.compile("^[A-Z0-9]{7,12}$"));
        // Portogallo (PT): Lettera iniziale + cifre (ripulito dal trattino "-")
        LICENSE_PATTERNS.put("PT", Pattern.compile("^[A-Z0-9]{7,12}$"));
        // Repubblica Ceca (CZ): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("CZ", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Romania (RO): 9 caratteri alfanumerici
        LICENSE_PATTERNS.put("RO", Pattern.compile("^[A-Z0-9]{9}$"));
        // Slovacchia (SK): 1 lettera + 7 cifre
        LICENSE_PATTERNS.put("SK", Pattern.compile("^[A-Z]\\d{7}$"));
        // Slovenia (SI): Da 8 a 9 cifre
        LICENSE_PATTERNS.put("SI", Pattern.compile("^\\d{8,9}$"));
        // Spagna (ES): 8 cifre + 1 lettera finale (spesso identico al documento DNI)
        LICENSE_PATTERNS.put("ES", Pattern.compile("^\\d{8}[A-Z]$"));
        // Svezia (SE): 10 cifre (ricalca il Personnummer, senza trattino)
        LICENSE_PATTERNS.put("SE", Pattern.compile("^\\d{10}$"));
        // Ungheria (HU): 2 lettere + 7 cifre
        LICENSE_PATTERNS.put("HU", Pattern.compile("^[A-Z]{2}\\d{7}$"));

        // --- PAESI EUROPEI EXTRA-UE / SCHENGEN ---
        // Regno Unito (GB): 16 caratteri alfanumerici (formato DVLA molto specifico)
        LICENSE_PATTERNS.put("GB", Pattern.compile("^[A-Z9]{5}\\d{6}[A-Z9]{2}\\d[A-Z]{2}$"));
        // Svizzera (CH): 12 cifre
        LICENSE_PATTERNS.put("CH", Pattern.compile("^\\d{12}$"));
        // Norvegia (NO): 11 cifre
        LICENSE_PATTERNS.put("NO", Pattern.compile("^\\d{11}$"));
        // Islanda (IS): 10 cifre
        LICENSE_PATTERNS.put("IS", Pattern.compile("^\\d{10}$"));
    }
	
    /**
     * Valida la patente di guida fornita in input applicando una catena di controlli sequenziali.
     * <p><b>Fasi dell'algoritmo di validazione:</b></p>
     * <ol>
     * <li><b>Pre-controllo:</b> Verifica immediata della presenza del dato (fallisce se nullo o stringa vuota).</li>
     * <li><b>Igienizzazione (Sanitization):</b> Rimozione dei caratteri separatori comunemente inseriti dagli utenti 
     * (spazi, virgole, punti, trattini, slash, underscore) tramite la Regex <code>[\s,\.\-/_]+</code>.</li>
     * <li><b>Normalizzazione:</b> Conversione forzata in caratteri maiuscoli (Uppercase).</li>
     * <li><b>Boundary Check:</b> Scarto immediato di stringhe con lunghezza fuori dal range consentito dai 
     * formati europei (minimo 5, massimo 20 caratteri) per evitare elaborazioni Regex inutili su dati palesemente errati.</li>
     * <li><b>Pattern Matching:</b> Ricerca parallela all'interno del dizionario. Restituisce <code>true</code> 
     * alla prima occorrenza valida trovata (metodo <code>anyMatch</code>).</li>
     * </ol>
     * @param value   La stringa inviata dal client rappresentante il numero di patente da validare.
     * @param context Il contesto di validazione che fornisce le API per personalizzare l'errore generato.
     * @return <code>true</code> se la stringa (dopo la sanitizzazione) matcha il formato di almeno 
     * uno dei Paesi censiti, <code>false</code> altrimenti.
     */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		String manipulatedValue = value.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		if(manipulatedValue.length() < 5 || manipulatedValue.length() > 20)
			return false;
		return LICENSE_PATTERNS.values().stream()
			.anyMatch(pattern -> pattern.matcher(manipulatedValue).matches());
	}
}
