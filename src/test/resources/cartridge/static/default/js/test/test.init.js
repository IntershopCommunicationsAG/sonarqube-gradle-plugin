/*global $:false */
/**
 * Initialize the dashboard and register global events to manage widgets.

 * @param {object} options initialization options, see defaults
 */
function initTest(options) {
	'use strict';

	var defaults = {
			token: false,
			dashboardId: false,
			dashboardSelector: '#dashboard',
			dashboardColumns: 4,
			widgetMargin: 5,
			widgetHeight: 230,
			widgetX: 1,
			widgetY: 4,
			widgetTemplateSelector: '#widget-tpl'
		},
		config = $.extend({}, defaults, options),
		$document = $(document),
		$dashboard = $(config.dashboardSelector);

	/**
	 * A Test represents a TestBO on the client-side.
	 *
	 * @param {object} d          Information about the dashboard
	 * @param {string} d.id       ID of the DashboardBO
	 * @param {object} d.$el      jQuery element displaying the dashboard
	 * @param {object} d.gridster gridster instance of the dashboard
	 */
	function Test(d) {
		this.widgets = {};
		this.id = d.id;
		this.$el = d.$el;
		this.gridster = d.gridster;
	}

	/**
	 * Remove the Dashboard on the server.
	 * @returns {object} promise to remove the Dashboard
	 */
	Dashboard.prototype.remove = function remove() {
		var deferred = $.Deferred();

		$.post('ViewDashboard-Delete', {
				SynchronizerToken: config.token,
				DashboardID: this.id
			})
			.done(function removeDone() {
				deferred.resolve();
			})
			.fail(deferred.reject);

		return deferred.promise();
	};

	/**
	 * Add a Widget to the Dashboard and persist it on the server.
	 * 
	 * @param   {object}  widgetData         Information about the widget
	 * @param   {string}  widgetData.id      ID of the WidgetBO (optional)
	 * @param   {string}  widgetData.typeId  TypeID of the WidgetBO
	 * @param   {string}  widgetData.title   Title of the WidgetBO
	 * @param   {string}  widgetData.url     Url of the WidgetBO (optional) 
	 * @param   {object}  coords        initial coordinates of the Widget on the Dashboard
	 * @param   {number}  coords.width  width of the widget
	 * @param   {number}  coords.height height of the widget
	 * @param   {number}  coords.col    left column of the widget
	 * @param   {number}  coords.row    top row of the widget
	 * @param   {boolean} [store=true]  if false Widget is only visually added without persisting it
	 * @returns {object}                promise to add the Widget
	 */
	Dashboard.prototype.addWidget = function addWidget(widgetData, coords, store) {
		var deferred = $.Deferred();
		var _this = this;
		
		function add(widget) {
			coords = coords || {};
			coords.width = coords.width || config.widgetX;
			coords.height = coords.height || config.widgetY;
			_this.gridster.add_widget(widget.$el.get(0), coords.width, coords.height, coords.col, coords.row);
			_this.widgets[widget.id] = widget;

			deferred.resolve(widget);
		}

		if (store === false) {
			var w = new Widget(widgetData);
			add(w);
		}
		else {
			$.post('ViewDashboard-AddWidget', {
					SynchronizerToken: config.token,
					DashboardID: this.id,
					WidgetTypeID: widgetData.typeId
				})
				.done(function addDone(addedWidget) {
					widgetData.id = addedWidget.id;
					var w = new Widget(widgetData);
					w.$el.addClass('widget-highlight');
					add(w);
				})
				.fail(deferred.reject);
		}

		return deferred.promise();
	};

	/**
	 * Removes a Widget from the Dashboard and persist it on the server.
	 * 
	 * @param   {Widget} widget  Widget object to be removed
	 * @returns {object}         promise to remove the Widget
	 */
	Dashboard.prototype.removeWidget = function removeWidget(widget) {
		var deferred = $.Deferred();
		var _this = this;
		
		$.post('ViewDashboard-DeleteWidget', {
				SynchronizerToken: config.token,
				DashboardID: this.id,
				WidgetID: widget.id
			})
			.done(function removeDone() {
				// remove widget
				_this.gridster.remove_widget(widget.$el, function afterRemove() {
					delete _this.widgets[widget.id];
					deferred.resolve();
				});
			})
			.fail(deferred.reject);
		
		return deferred.promise();
	};

	/**
	 * States whether the Dashboard has no Widgets
	 * 
	 * @returns {boolean} true if it has no Widgets
	 */
	Dashboard.prototype.isEmpty = function isEmpty() {
		return Object.keys(this.widgets).length === 0;
	};

	Dashboard.prototype.save = function save() {
		var state = this.gridster.serialize().map(function eachItem(item) {
			var data = {
				WidgetID: item.id,
				col: item.col,
				row: item.row,
				width: item.size_x,
				height: item.size_y
			};
			return data;
		});
		
		if (state.length === 0) {
			// nothing changed
			return $.Deferred().resolve().promise();
		}
		
		return $.post('ViewDashboard-SaveDashboardArrangement', {
				SynchronizerToken: config.token,
				DashboardID: this.id,
				WidgetData: JSON.stringify(state)
			});
	};

	/**
	 * A Widget represents a WidgetBO on the client-side.
	 *
	 * @param {object} w         Information about the widget
	 * @param {string} w.id      ID of the WidgetBO
	 * @param {string} w.typeId  TypeID of the WidgetBO
	 * @param {string} w.title   Title of the WidgetBO
	 * @param {string} w.url     Url of the WidgetBO
	 */
	function Widget(w) {
		this.id = w.id;
		this.typeId = w.typeId;
		this.title = w.title;
		
		this.url = w.url || "ViewWidget-Start?DashboardID=" + CurrentDashboard.id + "&WidgetID=" + this.id;
		this.$el = $($.parseHTML(Widget.template));
		this._id(this.id);
		
		this.$el
			.data('widget', this)
			.attr('data-typeid', this.typeId)
			.data('url', this.url)
			.find('.widget-iframe')
				.attr('src', this.url)
				.end()
			.find('.widget-title')
				.text(this.title);
		
		/* hide lock-overlay for external URLs */
		if (w.url) {
			this.$el.find('.widget-lock').hide();
		}
	}

	/**
	 * HTML template to create new Widgets.
	 */
	Widget.template = $(config.widgetTemplateSelector).html().trim();
	
	/**
	 * Sets the id of the Widget.
	 * 
	 * @private
	 * @param {string} id ID of the WidgetBO
	 */
	Widget.prototype._id = function _id(id) {
		this.id = id;
		this.$el
			// .data('id', id)
			.attr('data-id', id)
			.find('.widget-iframe')
				.attr('name', id);
	};

	/**
	 * Get the width of the Widget on the Dashboard.
	 *
	 * @returns {number} Number of columns it spans
	 */
	Widget.prototype.width = function width() {
		return this.$el.data('sizex');
	};

	/**
	 * Get the height of the Widget on the Dashboard.
	 *
	 * @returns {number} Number of rows it spans
	 */
	Widget.prototype.height = function height() {
		return this.$el.data('sizey');
	};

	/**
	 * Get the top row of the Widget on the Dashboard.
	 *
	 * @returns {number} Top row
	 */
	Widget.prototype.row = function row() {
		return this.$el.data('row');
	};

	/**
	 * Get the left column of the Widget on the Dashboard.
	 *
	 * @returns {number} Left column
	 */
	Widget.prototype.column = function column() {
		return this.$el.data('col');
	};

	/**
	 * Reload the Widget content from the server.
	 * 
	 * @param {String} [url] location of the widget content
	 */
	Widget.prototype.refresh = function refresh(url) {
		var $iframe = this.$el.find('.widget-iframe');
		if (!url) {
		    url = this.$el.data('url');
		}
		$iframe.attr('src', url);
	};
	
	/**
	 * Set the title of the Widget.
	 * 
	 * @param {String} [value] title of the widget
	 */
	Widget.prototype.setTitle = function setTitle(value) {
		this.$el.find('.widget-title').text(value);
	};

	function calcWidgetWidth() {
		var dashboardWidth = $('#main_content').innerWidth() - 40,
			width = parseInt(dashboardWidth / config.dashboardColumns - 2 * config.widgetMargin, 10);
		return width;
	}
	// initialize empty dashboard using gridster
	var gridster = $dashboard.gridster({
		widget_base_dimensions: [ calcWidgetWidth(), config.widgetHeight ],
		widget_margins: [ config.widgetMargin, config.widgetMargin ],
		widget_selector: '.widget',
		min_cols: config.dashboardColumns,
		max_cols: config.dashboardColumns,
		max_size_x: config.dashboardColumns,
		serialize_params: function ($widget, data) {
			data.id = $widget.data('id');
			return data;
		},
		draggable: {
			ignore_dragging: function (event) {
				var $handle = $(event.target);
				
				if ($handle.is('INPUT, TEXTAREA, SELECT, BUTTON')) {
					return true;
				}
				return !$handle.closest('.widget-handle').length;
			},
			start: function () {
				$dashboard.find('.widget-overlay').show();
			},
			stop: function () {
				$dashboard.find('.widget-overlay').hide();
				$document.trigger('draggedwidget.dashboard');
			}
		},
		resize: {
			enabled: true,
			max_size: [ config.dashboardColumns, Infinity ],
			start: function () {
				$dashboard.find('.widget-overlay').show();
			},
			stop: function () {
				$dashboard.find('.widget-overlay').hide();
				$document.trigger('resizedwidget.dashboard');
			}
		}
	}).data('gridster');
	
	// resize dashboard after window resize
	$(window).resize((function () {
		var timerId = -1;
		
		return function () {
			var widgetWidth = calcWidgetWidth();

			window.clearTimeout(timerId);
			timerId = window.setTimeout(function resizeDashboard() {
				gridster.resize_widget_dimensions({
					widget_base_dimensions: [ widgetWidth, config.widgetHeight ]
				});
				
				if (CurrentDashboard.isEmpty()) {
					// fix dashboard width if empty
					gridster.set_dom_grid_width();
				}
			}, 300);
		};
	})());

	// widget actions
	$dashboard.on('click', '.js-widget-action', function widgetAction(event) {
		event.preventDefault();
		var $button = $(this),
			action = $button.data('action'),
			$widget = $button.closest('.widget');
		
		switch (action) {
			case 'remove':
				$dialogDeleteWidget.data('$widget', $widget);
				$dialogDeleteWidget.dialog('open');
				break;
			case 'configure':
				var $iframe = $dialogConfigureWidget.children('iframe');
				$dialogConfigureWidget.data('$widget', $widget);
				
				var title = $dialogConfigureWidget.attr('data-title').replace('{0}', $widget.find('.widget-title').text());
				$dialogConfigureWidget.dialog('option', 'title', title)
				
				$iframe.attr('src', $iframe.data('src') + "?DashboardID=" + CurrentDashboard.id + "&WidgetID=" + $widget.data('id'));
				$dialogConfigureWidget.dialog('open');
				break;
		}
	});
	
	// Create current Dashboard and store it for further use
	window.CurrentDashboard = new Dashboard({
		id: config.dashboardId,
		$el: $dashboard,
		gridster: gridster
	});

	/* 
	 * add existing widget of dashboard:
	 *     $document.trigger('loadwidget.dashboard', [
	 *         {
	 *             id: '<WidgetBO:ID>',
	 *             typeId: '<WidgetBO:WidgetType:ID>',
	 *             title: '<istext with WidgetBO:WidgetType:DisplayNameKey>',
	 *             url: '<WidgetBO:WidgetType:RenderPipeline>'
	 *         }, {
	 *             column: <WidgetBO:PositionX>,
	 *             row: <WidgetBO:PositionY>,
	 *             width: <WidgetBO:SizeX>,
	 *             height: <WidgetBO:SizeY>
	 *         }
	 *     ]);
	 */
	$document.on('loadwidget.dashboard', function loadWidgetOfDashboard(event, widgetData, coords) {
		CurrentDashboard.addWidget(widgetData, coords, false)
			.done(function (widget) {
				$document.trigger('loadedwidget.dashboard', [ widget ]);
			});
	});

	/*
	 * add new widget to dashboard:
	 *     $document.trigger('addwidget.dashboard', [
	 *         {
	 *             typeId: '<WidgetType:ID>',
	 *             title: '<istext with WidgetType:DisplayNameKey>',
	 *             url: '<WidgetType:RenderPipeline>',
	 *             width: <WidgetType:SizeX>,
	 *             height: <WidgetType:SizeY>
	 *         },
	 *         successFn,
	 *         errorFn
	 *     ]);
	 */
	$document.on('addwidget.dashboard', function addWidgetToDashboard(event, widgetData, done, fail) {
		CurrentDashboard.addWidget(widgetData)
			.done(function (widget) {
				$document.trigger('addedwidget.dashboard', [ widget ]);
				(done || $.noop)(widget);
			})
			.fail(fail || $.noop);
	});
	
	/*
	 * remove widget from dashboard:
	 *     $document.trigger('removewidget.dashboard', [
	 *         $widget,
	 *         successFn,
	 *         errorFn
	 *     ]);
	 */
	$document.on('removewidget.dashboard', function removeWidgetFromDashboard(event, $widget, done, fail) {
		var widget = $widget.data('widget');
		CurrentDashboard.removeWidget(widget)
			.done(function () {
				$document.trigger('removedwidget.dashboard');
				(done || $.noop)();
			})
			.fail(fail  || $.noop);
	});
	
	/* 
	 * refreshes the iframe contents of a widget of the dashboard:
	 *   internal widget:
	 *     $document.trigger('refreshwidget.dashboard', ['<WidgetBO:ID>', '<WidgetBO:DisplayName>']);
	 *   external widget:
     *     $document.trigger('refreshwidget.dashboard', ['<WidgetBO:ID>', '<WidgetBO:DisplayName>', '<WidgetBO:URI>']);
	 */
	$document.on('refreshwidget.dashboard', function refreshWidgetOfDashboard(event, widgetID, widgetTitle, widgetURL) {		
		var widget = CurrentDashboard.widgets[widgetID];
		if (widget) {
			widget.setTitle(widgetTitle);
			widget.refresh(widgetURL);
		}
	});
	
	/*
	 * toggle empty dashboard message
	 */
	$document
		.on('loadedwidget.dashboard addedwidget.dashboard', function hideDashboardEmpty() {
			$('#dashboard-empty').hide();
			$(window).resize();
		})
		.on('removedwidget.dashboard', function showDashboardEmpty() {
			if (CurrentDashboard.isEmpty()) $('#dashboard-empty').show();
		});
	
	/*
	 * save current dashboard state:
	 *     $document.trigger('save.dashboard');
	 */
	$document.on('save.dashboard removedwidget.dashboard addedwidget.dashboard draggedwidget.dashboard resizedwidget.dashboard', function saveDashboard() {
		CurrentDashboard.save()
			.fail(function (jqXhr) {
				if (jqXhr.status !== 401) {
					throw new Error('Saving the current Dashboard failed.');
				}
			});
	});
	
	// (configure-widget-dialog) initialize
	var $dialogConfigureWidget = $('#widget-dialog-configuration');
	$dialogConfigureWidget.on('dialogopen', function onConfigureWidgetOpen() {
		// add highlight to current widget
		var $widget = $dialogConfigureWidget.data('$widget');
		window.setTimeout(function () {
			$widget.addClass('widget-highlight');
		}, 0);
	});
	$dialogConfigureWidget.on('dialogclose', function onConfigureWidgetClose() {
		$dashboard.find('.widget-highlight').removeClass('widget-highlight');
		$dialogConfigureWidget.removeData('widget');
		$dialogConfigureWidget.children('iframe').attr('src', '');
	});
	
	// (delete-widget-dialog) initialize
	var $dialogDeleteWidget = $('#widget-dialog-deleteconfirmation');
	$dialogDeleteWidget.on('dialogopen', function onOpen() {
		// add highlight to current widget
		var $widget = $dialogDeleteWidget.data('$widget');
		window.setTimeout(function () {
			$widget.addClass('widget-highlight');
		}, 0);
	});
	$dialogDeleteWidget.on('dialogclose', function onDeletWidgetClose() {
		$dashboard.find('.widget-highlight').removeClass('widget-highlight');
		$dialogDeleteWidget.removeData('widget');
	});

	// (delete-widget-dialog) confirm
	$('.js-widget-delete-ok').click(function startWidgetDeletion(event) {
		event.preventDefault();
		var $widget = $dialogDeleteWidget.data('$widget');
		
		$document.trigger('removewidget.dashboard', [
			$widget,
			function deleteSuccess() {
				$dialogDeleteWidget.dialog('close');
			},
			function deleteFail(jqXhr) {
				if (jqXhr.status !== 401) {
					throw new Error('Deleting the Widget failed.');
				}
			}
		]);
	});
	
	// (rename-dashboard-dialog) initialize
	var $dialogRenameDashboard = $('#dashboard-dialog-rename');
	$dialogRenameDashboard.on('dialogclose', function onRenameClose() {
	    var $iframe = $dialogRenameDashboard.children('iframe');
	    $iframe.attr('src', $iframe.data('src'));
	});
}
