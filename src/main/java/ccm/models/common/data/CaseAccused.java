package ccm.models.common.data;

import ccm.models.system.justin.JustinAccused;

public class CaseAccused {
    public static final String COMMA_STRING = ",";

    private String identifier;
    private String full_name;
    private String surname;
    private String given_1_name;
    private String given_2_name;
    private String given_3_name;
    private String proposed_process_type;
    private String proposed_appearance_date;
    private String crown_decision_code;
    private String offence_date;
    private Boolean indigenous_accused_yn;
    private String name_and_proposed_process_type;
    private String birth_date;  
    private Boolean hroip_yn;
    private Boolean do_lto_yn;

    public CaseAccused() {
    }

    public CaseAccused(JustinAccused ja) {
        setIdentifier(ja.getPart_id());
        setFull_name(ja.getAccused_name());
        setSurname(ja.getAccused_surname_nm());
        setGiven_1_name(ja.getAccused_given_1_nm());
        setGiven_2_name(ja.getAccused_given_2_nm());
        setGiven_3_name(ja.getAccused_given_3_nm());
        setProposed_process_type(ja.getProposed_process_type());
        setProposed_appearance_date(ja.getProposed_appr_date());
        setCrown_decision_code(ja.getCrown_decision());
        setOffence_date(ja.getOffence_date());
        setBirth_date(ja.getBirth_date());

        // Map 78
        setIndigenous_accused_yn(ja.getIndigenous_yn() != null && "Y".equalsIgnoreCase(ja.getIndigenous_yn()));
            
        setHroip_yn(ja.getHroip_yn() != null && "Y".equalsIgnoreCase(ja.getHroip_yn()));
        setDo_lto_yn(ja.getDo_lto_yn() != null && "Y".equalsIgnoreCase(ja.getDo_lto_yn()));

        StringBuilder name_process = new StringBuilder();
        if(ja.getProposed_process_type() != null) {
            name_process.append(ja.getProposed_process_type());
            name_process.append(" - ");
            name_process.append(ja.getAccused_name());
            setName_and_proposed_process_type(name_process.toString());
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getGiven_1_name() {
        return given_1_name;
    }

    public void setGiven_1_name(String given_1_name) {
        this.given_1_name = given_1_name;
    }

    public String getGiven_2_name() {
        return given_2_name;
    }

    public void setGiven_2_name(String given_2_name) {
        this.given_2_name = given_2_name;
    }

    public String getGiven_3_name() {
        return given_3_name;
    }

    public void setGiven_3_name(String given_3_name) {
        this.given_3_name = given_3_name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getProposed_process_type() {
        return proposed_process_type;
    }

    public void setProposed_process_type(String proposed_process_type) {
        this.proposed_process_type = proposed_process_type;
    }

    public String getProposed_appearance_date() {
        return proposed_appearance_date;
    }

    public void setProposed_appearance_date(String proposed_appearance_date) {
        this.proposed_appearance_date = proposed_appearance_date;
    }

    public String getCrown_decision_code() {
        return crown_decision_code;
    }

    public void setCrown_decision_code(String crown_decision_code) {
        this.crown_decision_code = crown_decision_code;
    }

    public String getOffence_date() {
        return offence_date;
    }

    public void setOffence_date(String offence_date) {
        this.offence_date = offence_date;
    }

    public Boolean getIndigenous_accused_yn() {
        return indigenous_accused_yn;
    }

    public void setIndigenous_accused_yn(Boolean indigenous_accused_yn) {
        this.indigenous_accused_yn = indigenous_accused_yn;
    }

    public String getName_and_proposed_process_type() {
        return name_and_proposed_process_type;
    }

    public void setName_and_proposed_process_type(String name_and_proposed_process_type) {
        this.name_and_proposed_process_type = name_and_proposed_process_type;
    }

    public String getBirth_date() {
        return birth_date;
    }

    public void setBirth_date(String birth_date) {
        this.birth_date = birth_date;
    }

    public Boolean getHroip_yn() {
        return hroip_yn;
    }

    public void setHroip_yn(Boolean hroip_yn) {
        this.hroip_yn = hroip_yn;
    }

    public Boolean getDo_lto_yn() {
        return do_lto_yn;
    }

    public void setDo_lto_yn(Boolean do_lto_yn) {
        this.do_lto_yn = do_lto_yn;
    }
    
}
