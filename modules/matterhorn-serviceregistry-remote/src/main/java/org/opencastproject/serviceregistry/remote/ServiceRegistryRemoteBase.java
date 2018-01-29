/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.serviceregistry.remote;

import static com.entwinemedia.fn.Stream.$;
import static org.apache.commons.lang3.StringUtils.isBlank;

import org.opencastproject.job.api.JaxbJob;
import org.opencastproject.job.api.JaxbJobList;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.Job.Status;
import org.opencastproject.job.api.JobParser;
import org.opencastproject.security.api.TrustedHttpClient;
import org.opencastproject.security.api.TrustedHttpClientException;
import org.opencastproject.serviceregistry.api.HostRegistration;
import org.opencastproject.serviceregistry.api.HostRegistrationParser;
import org.opencastproject.serviceregistry.api.IncidentService;
import org.opencastproject.serviceregistry.api.Incidents;
import org.opencastproject.serviceregistry.api.JaxbHostRegistrationList;
import org.opencastproject.serviceregistry.api.JaxbServiceRegistrationList;
import org.opencastproject.serviceregistry.api.JaxbServiceStatistics;
import org.opencastproject.serviceregistry.api.ServiceRegistration;
import org.opencastproject.serviceregistry.api.ServiceRegistrationParser;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.serviceregistry.api.ServiceStatistics;
import org.opencastproject.serviceregistry.api.SystemLoad;
import org.opencastproject.serviceregistry.api.SystemLoad.NodeLoad;
import org.opencastproject.serviceregistry.api.SystemLoadParser;
import org.opencastproject.util.HttpUtil;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.QueryStringBuilder;
import org.opencastproject.util.UrlSupport;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * This implementation of the remote service registry is able to provide the functionality specified by the api over
 * <code>HTTP</code> rather than by directly connecting to the database that is backing the service.
 * <p>
 * This means that it is suited to run inside protected environments as long as there is an implementation of the
 * service running somwhere that provides the matching communication endpoint, which is the case with the default
 * implementation at org.opencastproject.serviceregistry.impl.ServiceRegistryJpaImpl.
 * <p>
 * Other than with the other <code>-remote</code> implementations, this one needs to be configured to find it's
 * counterpart implementation. It may either point to a load balancer hiding a number of running instances or to one
 * specific instance.
 */
public abstract class ServiceRegistryRemoteBase implements ServiceRegistry {
  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(ServiceRegistryRemoteBase.class);

  /** Current job used to process job in the service registry. */
  private static final ThreadLocal<Job> currentJob = new ThreadLocal<Job>();

  /** Configuration key for the service registry */
  public static final String OPT_SERVICE_REGISTRY_URL = "org.opencastproject.serviceregistry.url";

  /** The http client to use when connecting to remote servers. */
  public abstract TrustedHttpClient getHttpClient();

  /** The incident service. */
  public abstract IncidentService getIncidentService();

  /** The complete URL of the actual service implementation, e.g. http://mh-admin:8080/services */
  public abstract String getServiceUrl();

  /** The base URL of this server, e.g. http://mh-client */
  public abstract String getServerUrl();

  private Incidents incidents;

  @Override
  public ServiceRegistration registerService(String serviceType, String host, String path)
          throws ServiceRegistryException {
    return registerService(serviceType, host, path, false);
  }

  @Override
  public void enableHost(String host) throws ServiceRegistryException, NotFoundException {
    final HttpPost post = post("enablehost");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("host", host));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Enabled host '" + host + "'.");
        return;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException("Host not found: " + host);
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to enable '" + host + "'", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to enable '" + host + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public void disableHost(String host) throws ServiceRegistryException, NotFoundException {
    final HttpPost post = post("disablehost");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("host", host));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Disabled host '" + host + "'.");
        return;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException("Host not found: " + host);
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to disable '" + host + "'", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to disable '" + host + "'. HTTP status=" + responseStatusCode);
  }

  /**
   * {@inheritDoc}
   * @throws ServiceRegistryException
   *
   * @see org.opencastproject.serviceregistry.api.ServiceRegistry#registerHost(String, String, long, int, float)
   */
  @Override
  public void registerHost(String host, String address, long memory, int cores, float maxLoad)
          throws ServiceRegistryException {
    final HttpPost post = post("registerhost");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("host", host));
      params.add(new BasicNameValuePair("address", address));
      params.add(new BasicNameValuePair("memory", Long.toString(memory)));
      params.add(new BasicNameValuePair("cores", Integer.toString(cores)));
      params.add(new BasicNameValuePair("maxLoad", Float.toString(maxLoad)));
      post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Registered '" + host + "'.");
        return;
      }
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to register '" + host + "'", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to register '" + host + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public void unregisterHost(String host) throws ServiceRegistryException {
    final HttpPost post = post("unregisterhost");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("host", host));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Unregistered '" + host + "'.");
        return;
      }
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to unregister '" + host + "'", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to unregister '" + host + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public ServiceRegistration registerService(String serviceType, String host, String path, boolean jobProducer)
          throws ServiceRegistryException {
    final HttpPost post = post("register");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("serviceType", serviceType));
      params.add(new BasicNameValuePair("host", host));
      params.add(new BasicNameValuePair("path", path));
      params.add(new BasicNameValuePair("jobProducer", Boolean.toString(jobProducer)));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        logger.info("Registered '" + serviceType + "' on host '" + host + "' with path '" + path + "'.");
        return ServiceRegistrationParser.parse(response.getEntity().getContent());
      }
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to register '" + serviceType + "' on host '" + host + "' with path '"
              + path + "'.", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to register '" + serviceType + "' on host '" + host + "' with path '"
            + path + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public void unRegisterService(String serviceType, String host) throws ServiceRegistryException {
    final HttpPost post = post("unregister");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("serviceType", serviceType));
      params.add(new BasicNameValuePair("host", host));
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params);
      post.setEntity(entity);
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Unregistered '" + serviceType + "' on host '" + host + "'.");
        return;
      }
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to register " + serviceType + " from host " + host, e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to unregister '" + serviceType + "' on host '" + host
            + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public void setMaintenanceStatus(String host, boolean maintenance) throws NotFoundException, ServiceRegistryException {
    final HttpPost post = post("maintenance");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("host", host));
      params.add(new BasicNameValuePair("maintenance", Boolean.toString(maintenance)));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Set maintenance mode on '" + host + "' to '" + maintenance + "'.");
        return;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException("Host not found: " + host);
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to set maintenance mode on " + host + " to '" + maintenance + "'", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to set maintenace mode on '" + host + "' to '" + maintenance
            + "'. HTTP status=" + responseStatusCode);
  }

  @Override
  public Job createJob(String type, String operation) throws ServiceRegistryException {
    return createJob(type, operation, null, null, true, 1.0f);
  }

  @Override
  public Job createJob(String type, String operation, Float jobLoad) throws ServiceRegistryException {
    return createJob(type, operation, null, null, true, jobLoad);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments) throws ServiceRegistryException {
    return createJob(type, operation, arguments, null, true, 1.0f);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, Float jobLoad) throws ServiceRegistryException {
    return createJob(type, operation, arguments, null, true, jobLoad);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload)
          throws ServiceRegistryException {
    return createJob(type, operation, arguments, payload, true, 1.0f);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload, Float jobLoad)
          throws ServiceRegistryException {
    return createJob(type, operation, arguments, payload, true, jobLoad);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload, boolean queueable)
          throws ServiceRegistryException {
    return createJob(type, operation, arguments, payload, queueable, getCurrentJob(), 1.0f);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload, boolean queueable, Float jobLoad)
          throws ServiceRegistryException {
    return createJob(type, operation, arguments, payload, queueable, getCurrentJob(), jobLoad);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload, boolean queueable,
          Job parentJob) throws ServiceRegistryException {
    return createJob(type, operation, arguments, payload, queueable, parentJob, 1.0f);
  }

  @Override
  public Job createJob(String type, String operation, List<String> arguments, String payload, boolean queueable,
          Job parentJob, Float jobLoad) throws ServiceRegistryException {
    final HttpPost post = post("job");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("jobType", type));
      params.add(new BasicNameValuePair("host", getServerUrl()));
      params.add(new BasicNameValuePair("operation", operation));
      if (arguments != null && !arguments.isEmpty()) {
        for (String argument : arguments) {
          params.add(new BasicNameValuePair("arg", argument));
        }
      }
      if (payload != null)
        params.add(new BasicNameValuePair("payload", payload));
      params.add(new BasicNameValuePair("start", Boolean.toString(queueable)));
      params.add(new BasicNameValuePair("jobLoad", Float.toString(jobLoad)));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_CREATED) {
        Job job = JobParser.parseJob(response.getEntity().getContent());
        logger.debug("Created a new job '{}'", job);
        return job;
      }
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to create a job of type '" + type, e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to create a job of type '" + type + " (" + responseStatusCode + ")");
  }

  @Override
  public Job updateJob(Job job) throws ServiceRegistryException, NotFoundException {
    final HttpPut put = put("job/" + job.getId() + ".xml");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("job", JobParser.toXml(new JaxbJob(job))));
      put.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new ServiceRegistryException("Can not url encode post parameters", e);
    } catch (IOException e) {
      throw new ServiceRegistryException("Can not serialize job " + job, e);
    }
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(put);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Updated job '{}'", job);
        return job;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException("Job not found: " + job.getId());
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceRegistryException("Unable to update " + job, e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to update " + job + " (" + responseStatusCode + ")");
  }

  @Override
  public Job getJob(long id) throws NotFoundException, ServiceRegistryException {
    final HttpGet get = get("job/" + id + ".xml");
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException("Unable to locate job " + id);
      }
      if (responseStatusCode == HttpStatus.SC_OK) {
        return JobParser.parseJob(response.getEntity().getContent());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get job id=" + id, e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to retrieve job " + id + " (" + responseStatusCode + ")");
  }

  @Override
  public List<Job> getChildJobs(long id) throws ServiceRegistryException {
    final HttpGet get = get("job/" + id + "/children.xml");
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        final JaxbJobList jaxbJobList = JobParser.parseJobList(response.getEntity().getContent());
        return $(jaxbJobList.getJobs()).map(JaxbJob.fnToJob()).toList();
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get job id=" + id, e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to retrieve job " + id + " (" + responseStatusCode + ")");
  }

  @Override
  public List<Job> getJobs(String serviceType, Status status) throws ServiceRegistryException {
    QueryStringBuilder qsb = new QueryStringBuilder("jobs.xml").add("serviceType", serviceType);
    if (status != null)
      qsb.add("status", status.toString());
    final HttpGet get = get(qsb.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        final JaxbJobList jaxbJobList = JobParser.parseJobList(response.getEntity().getContent());
        return $(jaxbJobList.getJobs()).map(JaxbJob.fnToJob()).toList();
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get jobs", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to retrieve jobs via http:" + response.getStatusLine());
  }

  @Override
  public List<String> getJobPayloads(String operation) throws ServiceRegistryException {
    if (operation == null) {
      throw new ServiceRegistryException("Operation must not be null");
    }
    QueryStringBuilder qsb = new QueryStringBuilder("job/payloads.json").add("operation", operation);
    final HttpGet get = get(qsb.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        return new Gson().fromJson(new InputStreamReader(response.getEntity().getContent()),
                new TypeToken<List<String>>() {
                }.getType());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get jobs", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to retrieve jobs via http:" + response.getStatusLine());
  }

  @Override
  public List<Job> getActiveJobs() throws ServiceRegistryException {
    QueryStringBuilder qsb = new QueryStringBuilder("activeJobs.xml");
    final HttpGet get = get(qsb.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        final JaxbJobList jaxbJobList = JobParser.parseJobList(response.getEntity().getContent());
        return $(jaxbJobList.getJobs()).map(JaxbJob.fnToJob()).toList();
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get active jobs", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to retrieve active jobs via http:" + response.getStatusLine());
  }

  @Override
  public Incidents incident() {
    if (incidents == null && getIncidentService() != null)
      incidents = new Incidents(this, getIncidentService());
    return incidents;
  }

  @Override
  public List<ServiceRegistration> getServiceRegistrationsByLoad(String serviceType) throws ServiceRegistryException {
    String servicePath = new QueryStringBuilder("available.xml").add("serviceType", serviceType).toString();
    final HttpGet get = get(servicePath);
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        JaxbServiceRegistrationList serviceList = ServiceRegistrationParser.parseRegistrations(response.getEntity()
                .getContent());
        return new ArrayList<ServiceRegistration>(serviceList.getRegistrations());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service registrations (" + responseStatusCode + ")");
  }

  @Override
  public List<ServiceRegistration> getServiceRegistrationsByHost(String host) throws ServiceRegistryException {
    String servicePath = new QueryStringBuilder("services.xml").add("host", host).toString();
    final HttpGet get = get(servicePath);
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        JaxbServiceRegistrationList serviceList = ServiceRegistrationParser.parseRegistrations(response.getEntity()
                .getContent());
        return new ArrayList<ServiceRegistration>(serviceList.getRegistrations());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service registrations (" + responseStatusCode + ")");
  }

  @Override
  public List<ServiceRegistration> getServiceRegistrationsByType(String serviceType) throws ServiceRegistryException {
    String servicePath = new QueryStringBuilder("services.xml").add("serviceType", serviceType).toString();
    final HttpGet get = get(servicePath);
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        JaxbServiceRegistrationList serviceList = ServiceRegistrationParser.parseRegistrations(response.getEntity()
                .getContent());
        return new ArrayList<ServiceRegistration>(serviceList.getRegistrations());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service registrations (" + responseStatusCode + ")");
  }

  @Override
  public ServiceRegistration getServiceRegistration(String serviceType, String host) throws ServiceRegistryException {
    if (isBlank(serviceType) || isBlank(host)) {
      throw new IllegalArgumentException("Service type and host must be provided to locate service registrations");
    }
    String servicePath = new QueryStringBuilder("services.xml").add("serviceType", serviceType).add("host", host)
            .toString();
    final HttpGet get = get(servicePath);
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        return ServiceRegistrationParser.parse(response.getEntity().getContent());
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        return null;
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service registrations (" + responseStatusCode + ")");
  }

  @Override
  public List<ServiceRegistration> getServiceRegistrations() throws ServiceRegistryException {
    final HttpGet get = get("services.xml");
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        JaxbServiceRegistrationList serviceList = ServiceRegistrationParser.parseRegistrations(response.getEntity()
                .getContent());
        return new ArrayList<ServiceRegistration>(serviceList.getRegistrations());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service registrations (" + responseStatusCode + ")");
  }

  @Override
  public List<HostRegistration> getHostRegistrations() throws ServiceRegistryException {
    final HttpGet get = get("hosts.xml");
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        JaxbHostRegistrationList serviceList = HostRegistrationParser.parseRegistrations(response.getEntity()
                .getContent());
        return new ArrayList<HostRegistration>(serviceList.getRegistrations());
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get host registrations", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get host registrations (" + responseStatusCode + ")");
  }

  @Override
  public List<ServiceStatistics> getServiceStatistics() throws ServiceRegistryException {
    final HttpGet get = get("statistics.xml");
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        List<JaxbServiceStatistics> stats = ServiceRegistrationParser
                .parseStatistics(response.getEntity().getContent()).getStats();
        return new ArrayList<ServiceStatistics>(stats);
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service statistics", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service statistics (" + responseStatusCode + ")");
  }

  @Override
  public long count(String serviceType, Status status) throws ServiceRegistryException {
    return count(serviceType, null, null, status);
  }

  @Override
  public long countByHost(String serviceType, String host, Status status) throws ServiceRegistryException {
    if (isBlank(serviceType))
      throw new IllegalArgumentException("Service type must not be null");
    if (isBlank(host))
      throw new IllegalArgumentException("Host must not be null");
    if (status == null)
      throw new IllegalArgumentException("Status must not be null");
    return count(serviceType, host, null, status);
  }

  @Override
  public long countByOperation(String serviceType, String operation, Status status) throws ServiceRegistryException {
    if (isBlank(serviceType))
      throw new IllegalArgumentException("Service type must not be null");
    if (isBlank(operation))
      throw new IllegalArgumentException("Operation must not be null");
    if (status == null)
      throw new IllegalArgumentException("Status must not be null");
    return count(serviceType, null, operation, status);
  }

  @Override
  public long count(String serviceType, String host, String operation, Status status) throws ServiceRegistryException {
    QueryStringBuilder queryStringBuilder = new QueryStringBuilder("count");

    if (StringUtils.isNotBlank(serviceType))
      queryStringBuilder.add("serviceType", serviceType);

    if (status != null)
      queryStringBuilder.add("status", status.toString());

    if (StringUtils.isNotBlank(host))
      queryStringBuilder.add("host", host);

    if (StringUtils.isNotBlank(operation))
      queryStringBuilder.add("operation", operation);

    final HttpGet get = get(queryStringBuilder.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        return Long.parseLong(EntityUtils.toString(response.getEntity()));
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service statistics", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service statistics (" + responseStatusCode + ")");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.serviceregistry.api.ServiceRegistry#countOfAbnormalServices()
   */
  @Override
  public long countOfAbnormalServices() throws ServiceRegistryException {
    QueryStringBuilder queryStringBuilder = new QueryStringBuilder("servicewarnings");
    final HttpGet get = get(queryStringBuilder.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        return Long.parseLong(EntityUtils.toString(response.getEntity()));
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get service statistics", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get service statistics (" + responseStatusCode + ")");
  }

  @Override
  public SystemLoad getCurrentHostLoads() throws ServiceRegistryException {
    QueryStringBuilder queryStringBuilder = new QueryStringBuilder("currentload");

    final HttpGet get = get(queryStringBuilder.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        try (InputStream in = response.getEntity().getContent()) {
          SystemLoad systemLoad = SystemLoadParser.parse(in);
          return systemLoad;
        }
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get node loads", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get node loads (" + responseStatusCode + ")");
  }

  /** Note: This is a highly inefficient implementation, but this bundle is currently dead code anyway */
  public float getOwnLoad() throws ServiceRegistryException {
    SystemLoad loads = getCurrentHostLoads();
    return loads.get(getServerUrl()).getLoadFactor();
  }

  private SystemLoad getMaxLoads(String host) throws ServiceRegistryException {
    QueryStringBuilder queryStringBuilder = new QueryStringBuilder("maxload");

    if (StringUtils.isNotBlank(StringUtils.trimToEmpty(host)))
      queryStringBuilder.add("host", StringUtils.trimToEmpty(host));
    final HttpGet get = get(queryStringBuilder.toString());
    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(get);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_OK) {
        SystemLoad systemLoad = SystemLoadParser.parse(response.getEntity().getContent());
        return systemLoad;
      }
    } catch (IOException e) {
      throw new ServiceRegistryException("Unable to get node loads", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to get node loads (" + responseStatusCode + ")");
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.serviceregistry.api.ServiceRegistry#getMaxLoads()
   */
  @Override
  public SystemLoad getMaxLoads() throws ServiceRegistryException {
    return getMaxLoads(null);
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.serviceregistry.api.ServiceRegistry#getMaxLoadOnNode(java.lang.String)
   */
  @Override
  public NodeLoad getMaxLoadOnNode(String host) throws ServiceRegistryException {
    return getMaxLoads(host).get(host);
  }

  @Override
  public void sanitize(String serviceType, String host) throws NotFoundException {
    final HttpPost post = post("sanitize");
    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("serviceType", serviceType));
      params.add(new BasicNameValuePair("host", host));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Can not url encode post parameters", e);
    }

    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(post);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        return;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to get service statistics", e);
    } finally {
      getHttpClient().close(response);
    }
    throw new IllegalStateException("Unable to get service statistics (" + responseStatusCode + ")");
  }

  @Override
  public void removeJob(long id) throws NotFoundException, ServiceRegistryException {
    HttpDelete delete = new HttpDelete(UrlSupport.concat(getServiceUrl(), "job", String.valueOf(id)));

    HttpResponse response = null;
    int responseStatusCode;
    try {
      response = getHttpClient().execute(delete);
      responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        return;
      } else if (responseStatusCode == HttpStatus.SC_NOT_FOUND) {
        throw new NotFoundException();
      }
    } catch (TrustedHttpClientException e) {
      throw new ServiceRegistryException(e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to remove job with ID " + id + " (" + responseStatusCode + ")");
  }

  @Override
  public void removeParentlessJobs(int lifetime) throws ServiceRegistryException {
    HttpPost post = post("removeparentlessjobs");

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("lifetime", String.valueOf(lifetime)));
      post.setEntity(new UrlEncodedFormEntity(params));
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }

    HttpResponse response = null;
    try {
      response = getHttpClient().execute(post);
      int responseStatusCode = response.getStatusLine().getStatusCode();
      if (responseStatusCode == HttpStatus.SC_NO_CONTENT) {
        logger.info("Parentless jobs successfully removed");
        return;
      }
    } catch (TrustedHttpClientException e) {
      throw new ServiceRegistryException(e);
    } finally {
      getHttpClient().close(response);
    }
    throw new ServiceRegistryException("Unable to remove parentless jobs");
  }

  @Override
  public Job getCurrentJob() {
    return currentJob.get();
  }

  @Override
  public void setCurrentJob(Job job) {
    currentJob.set(job);
  }

  /** Create a GET request. */
  private HttpGet get(String action) {
    return new HttpGet(HttpUtil.path(getServiceUrl(), action));
  }

  /** Create a POST request. */
  private HttpPost post(String action) {
    return new HttpPost(HttpUtil.path(getServiceUrl(), action));
  }

  /** Create a PUT request. */
  private HttpPut put(String action) {
    return new HttpPut(HttpUtil.path(getServiceUrl(), action));
  }
}
