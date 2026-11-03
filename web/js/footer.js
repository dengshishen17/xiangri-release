/**
 * 巷里 XIANGRI · 底部悬浮胶囊导航
 * 契约：组件名 footBar，prop activeBtn，方法 toPage(i)
 *   toPage(0) -> /blog-edit.html
 *   toPage(1) -> /
 *   toPage(4) -> /info.html
 *   toPage(2) / toPage(3) 保持占位（无业务跳转）
 */
Vue.component("footBar", {
  template: `
    <div class="dock-wrap">
      <nav class="dock">
        <div class="dock-item" :class="{ 'is-active': activeBtn === 1 }" @click="toPage(1)">
          <div class="di-icon">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 10.5 12 4l8 6.5"/>
              <path d="M6 10v9.5h12V10"/>
              <path d="M10 19.5v-5h4v5"/>
            </svg>
          </div>
          <div class="di-label">发现</div>
        </div>
        <div class="dock-item" :class="{ 'is-active': activeBtn === 2 }" @click="toPage(2)">
          <div class="di-icon">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9.5 20.5c-3 0-5-2-5-4.5C4.5 11.5 8 6 12 6s7.5 5.5 7.5 10c0 2.5-2 4.5-5 4.5z"/>
              <path d="M12 6v14.5"/>
              <path d="M12 6c-1.6 0-3 1.3-3 3 0 1.5 1.2 2.6 3 2.6s3-1.1 3-2.6c0-1.7-1.4-3-3-3z"/>
            </svg>
          </div>
          <div class="di-label">地图</div>
        </div>
        <div class="dock-publish" @click="toPage(0)">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M12 5v14M5 12h14"/>
          </svg>
        </div>
        <div class="dock-item" :class="{ 'is-active': activeBtn === 3 }" @click="toPage(3)">
          <div class="di-icon">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 12.5c0 3.9-3.6 7-8 7-1 0-2-.2-2.9-.5L4 20l1.4-3.8C4.5 15.2 4 13.9 4 12.5c0-3.9 3.6-7 8-7s8 3.1 8 7z"/>
            </svg>
          </div>
          <div class="di-label">消息</div>
        </div>
        <div class="dock-item" :class="{ 'is-active': activeBtn === 4 }" @click="toPage(4)">
          <div class="di-icon">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="8.5" r="3.6"/>
              <path d="M4.8 19.5c1.2-3.4 4-5 7.2-5s6 1.6 7.2 5"/>
            </svg>
          </div>
          <div class="di-label">我的</div>
        </div>
      </nav>
    </div>
  `,
  data() {
    return {}
  },
  props: ['activeBtn'],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html"
      } else if (i === 4) {
        location.href = "/info.html"
      } else if (i === 1) {
        location.href = "/"
      }
    }
  }
})
