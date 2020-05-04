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

/* global $, Mustache, i18ndata */

'use strict';

const player = '/play/';

var currentpage,
    defaultLang = i18ndata['en-US'],
    lang = defaultLang;
var downloadLinks = [];  // Key-Value pairs (Index: URL)
var downloadIndex = 0;

function matchLanguage(lang) {
  // break for too short codes
  if (lang.length < 2) {
    return defaultLang;
  }
  // Check for exact match
  if (lang in i18ndata) {
    return i18ndata[lang];
  }
  // Check if there is a more specific language (e.g. 'en-US' if 'en' is requested)
  for (const key of Object.keys(i18ndata)) {
    if (key.startsWith(lang)) {
      return i18ndata[key];
    }
  }
  // check if there is a less specific language
  return matchLanguage(lang.substring(0, lang.length - 1));
}

function tryLocalDate(date) {
  try {
    return new Date(date).toLocaleString();
  } catch(err) {
    return date;
  }
}

function i18n(key) {
  return lang[key];
}

function getSeries() {
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has('series')) {
    return urlParams.get('series');
  }
  return '';
}

function capitalize(s) {
  if (typeof s !== 'string') {
    return '';
  }
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function sortResolutions(descA, descB) { // eslint-disable-line no-unused-vars
  var descAWidth = parseInt(descA.split('x')[0]);
  var descBWidth = parseInt(descB.split('x')[0]);
  if(descAWidth > descBWidth) {
    return -1;  // Returns descB
  }
  if(descBWidth > descAWidth) {
    return 1;
  }

  var descAHeight = parseInt(descA.split('x')[1]);
  var descBHeight = parseInt(descB.split('x')[1]);
  if(descAHeight > descBHeight) {
    return -1;
  }
  if(descBHeight > descAHeight) {
    return 1;
  }

  return 0;
}

// Called by the HTML. Only exists to "hide" the static download URLs in this js.
function getLink(index) { // eslint-disable-line no-unused-vars
  if (typeof downloadLinks !== 'undefined') {
    if(downloadLinks.length > index && index >= 0) {
      // Option 1: Open a new window. Safe, should be supported everywhere.
      //window.location.href = downloadLinks[index];

      // Option 2: Create a temporary HTML element. Probably fine, seems kinda hacky?
      // Creating an invisible element
      var element = document.createElement('a');
      element.setAttribute('href', downloadLinks[index]);                       // filepath
      element.setAttribute('download', downloadLinks[index].split('/').pop());  // filename

      // Add the element, click it and remove it before anyone notices it was even there
      document.body.appendChild(element);
      element.click();
      document.body.removeChild(element);
    }
  }
}

function loadPage(page) {

  var limit = 15,
      offset = (page - 1) * limit,
      series = getSeries(),
      url = '/search/episode.json?limit=' + limit + '&offset=' + offset;

  currentpage = page;

  // attach series query if a series is requested
  if (series) {
    url += '&sid=' + series;
  }

  // load spinner
  $('main').html($('#template-loading').html());

  $.getJSON(url, function( data ) {
    data = data['search-results'];
    var rendered = '',
        results = [],
        total = parseInt(data.total);

    if (total > 0) {
      results = Array.isArray(data.result) ? data.result : [data.result];
    }

    for (var i = 0; i < results.length; i++) {
      var episode = results[i],
          i18ncreator = Mustache.render(i18n('CREATOR'), {creator: episode.dcCreator}),
          i18nDownloadButton = Mustache.render(i18n('DOWNLOADBUTTON')),
          template = $('#template-episode').html(),
          tpldata = {
            player: player + episode.id,
            title: episode.dcTitle,
            i18ncreator: i18ncreator,
            i18nDownloadButton: i18nDownloadButton,
            created: tryLocalDate(episode.dcCreated)};

      // get preview image
      var attachments = episode.mediapackage.attachments.attachment;
      attachments = Array.isArray(attachments) ? attachments : [attachments];
      for (var j = 0; j < attachments.length; j++) {
        if (attachments[j].type.endsWith('/search+preview')) {
          tpldata['image'] = attachments[j].url;
          break;
        }
      }

      // Download buttons
      // Create a button for each track that contains a link to a mp4 or webm
      var buttonData = [];
      var tracks = episode.mediapackage.media.track;
      tracks = Array.isArray(tracks) ? tracks : [tracks];
      for (const track of tracks) {
        if( track.url.endsWith('mp4') || track.url.endsWith('webm')) {

          // Store links in global var
          downloadIndex++;
          downloadLinks[downloadIndex] = track.url;

          // Collect output to mustache
          buttonData.push ({
            'download-button-name' : capitalize( track.type.split('/')[0] ),
            'download-button-description': track.video.resolution,
            'download-index': downloadIndex});
        }
      }

      // Sort tracks by name. If names are the same, sort by description
      buttonData.sort(function(a, b){
        var nameA = a['download-button-name'].toLowerCase(),
            nameB = b['download-button-name'].toLowerCase();
        var descA = a['download-button-description'].toLowerCase(),
            descB = b['download-button-description'].toLowerCase();

        if(nameA > nameB) {
          return 1;
        }
        if(nameB > nameA) {
          return -1;
        }

        return sortResolutions(descA, descB);
      });

      tpldata['download'] = buttonData;

      // render template
      rendered += Mustache.render(template, tpldata);
    }

    // render episode view
    $('main').html(rendered);

    // render result information
    var resultTemplate = i18n('RESULTS'),
        resultTplData = {
          total: total,
          range: {
            begin: Math.min(offset + 1, total),
            end: offset + parseInt(data.limit)
          }
        };
    $('header').text(Mustache.render(resultTemplate, resultTplData));

    // render pagination
    $('footer').pagination({
      dataSource: Array(total),
      pageSize: limit,
      pageNumber: currentpage,
      callback: function(data, pagination) {
        if (pagination.pageNumber != currentpage) {
          loadPage(pagination.pageNumber);
        }
      }
    });

  });

}

$(document).ready(function() {
  lang = matchLanguage(navigator.language);
  loadPage(1);
});
