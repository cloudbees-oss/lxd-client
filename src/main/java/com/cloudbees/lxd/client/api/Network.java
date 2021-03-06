
package com.cloudbees.lxd.client.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Generated;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "managed",
    "name",
    "type",
    "used_by"
})
public class Network extends NetworkPut
{

    /**
     *
     *
     */
    @JsonProperty("managed")
    private Boolean managed;
    /**
     *
     *
     */
    @JsonProperty("name")
    private String name;
    /**
     *
     *
     */
    @JsonProperty("type")
    private String type;
    /**
     *
     *
     */
    @JsonProperty("used_by")
    private List<String> usedBy = new ArrayList<String>();

    /**
     * No args constructor for use in serialization
     *
     */
    public Network() {
    }

    /**
     *
     *
     * @return
     *     The managed
     */
    @JsonProperty("managed")
    public Boolean getManaged() {
        return managed;
    }

    /**
     *
     *
     * @param managed
     *     The managed
     */
    @JsonProperty("managed")
    public void setManaged(Boolean managed) {
        this.managed = managed;
    }

    /**
     *
     *
     * @return
     *     The name
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     *
     *
     * @param name
     *     The name
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     *
     * @return
     *     The type
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     *
     *
     * @param type
     *     The type
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    /**
     *
     *
     * @return
     *     The usedBy
     */
    @JsonProperty("used_by")
    public List<String> getUsedBy() {
        return usedBy;
    }

    /**
     *
     *
     * @param usedBy
     *     The used_by
     */
    @JsonProperty("used_by")
    public void setUsedBy(List<String> usedBy) {
        this.usedBy = usedBy;
    }
}
